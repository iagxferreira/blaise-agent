package dev.blaiseagent.agent

import dev.blaiseagent.state.ChatMessage
import dev.blaiseagent.state.MessageRole
import dev.blaiseagent.state.MessageStatus
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import dev.langchain4j.data.message.ChatMessage as ModelMessage

/** Each collection owns one bounded turn. Only native model tool requests execute. */
class OllamaChatAgent(
    private val model: StreamingChatModel,
    private val wooviConnectionTool: WooviConnectionTool? = null,
) : ChatAgent {
    constructor(
        baseUrl: String,
        modelName: String,
        timeout: Duration = Duration.ofSeconds(90),
        wooviConnectionTool: WooviConnectionTool? = null,
    ) : this(
        OllamaStreamingChatModel.builder().baseUrl(baseUrl).modelName(modelName)
            .temperature(0.2).timeout(timeout).build(),
        wooviConnectionTool,
    )

    override fun stream(messages: List<ChatMessage>): Flow<String> =
        streamEvents(messages).filterIsInstance<AgentEvent.Text>().map { it.value }

    override fun streamEvents(messages: List<ChatMessage>): Flow<AgentEvent> = callbackFlow {
        val history = mutableListOf<ModelMessage>(SystemMessage.from(PromptComposer.compose()))
        val completedCallIds = messages.filter { it.status == MessageStatus.ToolResult }.mapNotNull { it.toolCallId }.toSet()
        val callIds = messages.filter { it.status == MessageStatus.ToolCall }.mapNotNull { it.toolCallId }.toSet()
        history += messages.mapNotNull {
            when {
                it.status == MessageStatus.ToolCall -> if (it.toolCallId in completedCallIds && it.toolName != null) {
                    AiMessage.from(ToolExecutionRequest.builder().id(it.toolCallId).name(it.toolName).arguments("{}").build())
                } else null
                it.status == MessageStatus.ToolResult -> if (it.toolCallId in callIds && it.toolName != null) {
                    ToolExecutionResultMessage.from(it.toolCallId, it.toolName, it.text)
                } else null
                it.role == MessageRole.User -> UserMessage.from(it.text)
                else -> AiMessage.from(it.text)
            }
        }
        val worker = launch {
            try {
                var executed = false
                repeat(2) {
                    val completion = CompletableDeferred<ChatResponse>()
                    val request = ChatRequest.builder().messages(history.toList()).apply {
                        if (!executed && wooviConnectionTool != null) toolSpecifications(wooviConnectionTool.specification)
                    }.build()
                    model.chat(request, object : StreamingChatResponseHandler {
                        override fun onPartialResponse(partialResponse: String) {
                            trySend(AgentEvent.Text(partialResponse))
                        }
                        override fun onCompleteResponse(response: ChatResponse) {
                            completion.complete(response)
                        }
                        override fun onError(error: Throwable) {
                            completion.completeExceptionally(error)
                        }
                    })
                    val response = completion.await().aiMessage()
                    val calls = response.toolExecutionRequests()
                    if (calls.isEmpty()) {
                        if (runCatching { parseTextToolCall(response.text().orEmpty()) }.getOrNull() != null) {
                            send(AgentEvent.Notice(
                                "The model described a tool call as text but did not invoke it. " +
                                    "No operation was executed for this response. " +
                                    "Select a model verified for native tool calling, or use Settings → Woovi → Test connection.",
                            ))
                        }
                        close()
                        return@launch
                    }
                    // Reject the entire batch before executing anything. A turn permits one check.
                    val call = calls.singleOrNull()
                    val arguments = call?.let {
                        runCatching { Json.parseToJsonElement(it.arguments()) as? JsonObject }.getOrNull()
                    }
                    if (executed || call == null || wooviConnectionTool == null ||
                        call.name() != wooviConnectionTool.specification.name() || arguments == null || arguments.isNotEmpty()) {
                        error("Invalid model tool request")
                    }
                    executed = true
                    send(AgentEvent.ToolCall(call.name(), call.id()))
                    val result = try {
                        wooviConnectionTool.execute()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        "Woovi connection test could not be completed."
                    }
                    send(AgentEvent.ToolResult(call.name(), result, call.id()))
                    history += response
                    history += ToolExecutionResultMessage.from(call, result)
                }
                close()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                close(error)
            }
        }
        awaitClose { worker.cancel() }
    }.buffer(Channel.UNLIMITED)
}

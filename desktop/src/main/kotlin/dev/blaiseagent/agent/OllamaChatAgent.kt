package dev.blaiseagent.agent

import dev.blaiseagent.state.ChatMessage
import dev.blaiseagent.state.MessageRole
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage as LangChainMessage
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.time.Duration
import java.util.UUID

/** LangChain4j adapter for one request model snapshot. */
class OllamaChatAgent(
    baseUrl: String,
    modelName: String,
    timeout: Duration = Duration.ofSeconds(90),
    private val wooviConnectionTool: WooviConnectionTool? = null,
) : ChatAgent {
    private val model: StreamingChatModel = OllamaStreamingChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .temperature(0.2)
        .timeout(timeout)
        .build()

    override fun stream(messages: List<ChatMessage>): Flow<String> =
        streamEvents(messages).filterIsInstance<AgentEvent.Text>().map { it.value }

    override fun streamEvents(messages: List<ChatMessage>): Flow<AgentEvent> = callbackFlow {
        val langChainMessages = listOf(SystemMessage.from(AGENT_INSTRUCTIONS)) + messages.map { it.toLangChainMessage() }
        val bufferedResponse = StringBuilder()
        var toolRounds = 0
        lateinit var handler: StreamingChatResponseHandler
        handler = object : StreamingChatResponseHandler {
            override fun onPartialResponse(partialResponse: String) {
                if (wooviConnectionTool == null) trySend(AgentEvent.Text(partialResponse))
                else bufferedResponse.append(partialResponse)
            }

            override fun onCompleteResponse(response: dev.langchain4j.model.chat.response.ChatResponse) {
                val tool = wooviConnectionTool
                val nativeRequests = response.aiMessage().toolExecutionRequests()
                val textCall = if (tool != null && nativeRequests.isEmpty()) {
                    parseTextToolCall(bufferedResponse.toString())
                } else {
                    null
                }
                val supportedToolName = tool?.specification?.name()
                val unsupportedNativeRequest = nativeRequests.any { it.name() != supportedToolName }
                if (unsupportedNativeRequest || (textCall != null && textCall.name != supportedToolName)) {
                    trySend(AgentEvent.Text("I couldn't interpret that response from the model."))
                    close()
                    return
                }
                val requests = when {
                    nativeRequests.isNotEmpty() -> nativeRequests
                    textCall != null -> listOf(
                        ToolExecutionRequest.builder()
                            .id(UUID.randomUUID().toString())
                            .name(textCall.name)
                            .arguments(textCall.arguments)
                            .build(),
                    )
                    else -> emptyList()
                }
                if (tool == null || requests.isEmpty()) {
                    if (bufferedResponse.isNotEmpty()) trySend(AgentEvent.Text(bufferedResponse.toString()))
                    close()
                    return
                }

                toolRounds++
                if (toolRounds > 1) {
                    trySend(AgentEvent.Text("The Woovi connection result is shown above."))
                    close()
                    return
                }
                val messagesWithToolResults = langChainMessages.toMutableList()
                messagesWithToolResults += if (nativeRequests.isNotEmpty()) response.aiMessage() else AiMessage.from(requests)
                requests.forEach { request ->
                    trySend(AgentEvent.ToolCall(request.name()))
                    val result = if (request.name() == tool.specification.name()) {
                        runCatching { runBlocking { tool.execute() } }
                            .getOrDefault("Woovi connection test could not be completed.")
                    } else {
                        "Tool ${request.name()} is not available."
                    }
                    trySend(AgentEvent.ToolResult(request.name(), result))
                    messagesWithToolResults += ToolExecutionResultMessage.from(request, result)
                }
                bufferedResponse.clear()
                model.chat(
                    ChatRequest.builder()
                        .messages(messagesWithToolResults)
                        .toolSpecifications(tool.specification)
                        .build(),
                    handler,
                )
            }

            override fun onError(error: Throwable) {
                close(error)
            }
        }
        if (wooviConnectionTool == null) {
            model.chat(langChainMessages, handler)
        } else {
            model.chat(
                ChatRequest.builder()
                    .messages(langChainMessages)
                    .toolSpecifications(wooviConnectionTool.specification)
                    .build(),
                handler,
            )
        }
        awaitClose()
    }

    private companion object {
        const val AGENT_INSTRUCTIONS = """
            You are Blaise, a local financial assistant. Respond conversationally to normal messages.
            Only call test_woovi_connection when the user explicitly asks to test or check Woovi, its API key, configuration, or connection.
            Never call payment tools for greetings or unrelated questions.
            After receiving a tool result, summarize it and do not call the same tool again for the current request.
            Never emit tool-call JSON as ordinary text.
        """
    }
}

private fun ChatMessage.toLangChainMessage(): LangChainMessage = when (role) {
    MessageRole.User -> UserMessage.from(text)
    MessageRole.Assistant -> AiMessage.from(text)
}

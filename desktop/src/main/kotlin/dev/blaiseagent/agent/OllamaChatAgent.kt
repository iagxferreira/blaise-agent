package dev.blaiseagent.agent

import dev.blaiseagent.state.ChatMessage
import dev.blaiseagent.state.MessageRole
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage as LangChainMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import dev.langchain4j.model.ollama.OllamaStreamingChatModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.Duration

/** LangChain4j adapter for one request model snapshot. */
class OllamaChatAgent(
    baseUrl: String,
    modelName: String,
    timeout: Duration = Duration.ofSeconds(90),
) : ChatAgent {
    private val model: StreamingChatModel = OllamaStreamingChatModel.builder()
        .baseUrl(baseUrl)
        .modelName(modelName)
        .temperature(0.2)
        .timeout(timeout)
        .build()

    override fun stream(messages: List<ChatMessage>): Flow<String> = callbackFlow {
        val langChainMessages = messages.map { it.toLangChainMessage() }
        model.chat(langChainMessages, object : StreamingChatResponseHandler {
            override fun onPartialResponse(partialResponse: String) {
                trySend(partialResponse)
            }

            override fun onCompleteResponse(response: dev.langchain4j.model.chat.response.ChatResponse) {
                close()
            }

            override fun onError(error: Throwable) {
                close(error)
            }
        })
        awaitClose()
    }
}

private fun ChatMessage.toLangChainMessage(): LangChainMessage = when (role) {
    MessageRole.User -> UserMessage.from(text)
    MessageRole.Assistant -> AiMessage.from(text)
}

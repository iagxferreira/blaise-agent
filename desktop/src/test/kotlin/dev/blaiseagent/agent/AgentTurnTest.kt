package dev.blaiseagent.agent

import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.model.chat.StreamingChatModel
import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import dev.blaiseagent.config.CredentialKey
import dev.blaiseagent.config.CredentialStore
import dev.blaiseagent.config.WooviEnvironment
import dev.blaiseagent.payments.WooviConnection
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.data.message.ToolExecutionResultMessage

class AgentTurnTest {
    @Test
    fun textToolPayloadProducesAnApplicationNoticeRatherThanExecution() = runTest {
        val payload = """{"name":"test_woovi_connection","arguments":{}}"""
        val model = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                handler.onPartialResponse(payload)
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(payload)).build())
            }
        }
        val events = OllamaChatAgent(model).streamEvents(emptyList()).toList()
        assertTrue(events.last() is AgentEvent.Notice)
        assertTrue(events.none { it is AgentEvent.ToolCall })
    }

    @Test
    fun realToolExchangeReturnsEvidenceToModelAndDisablesFurtherTools() = runTest {
        var executions = 0
        var requests = 0
        val store = object : CredentialStore {
            override suspend fun contains(key: CredentialKey) = true
            override suspend fun read(key: CredentialKey) = "synthetic-secret"
            override suspend fun write(key: CredentialKey, value: String) = Unit
            override suspend fun delete(key: CredentialKey) = Unit
        }
        val tool = WooviConnectionTool(store, { WooviEnvironment.Sandbox }) { _, _ ->
            executions++
            WooviConnection.Ready
        }
        val model = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                requests++
                if (requests == 1) {
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from(
                        ToolExecutionRequest.builder().id("call-1").name("test_woovi_connection").arguments("{}").build(),
                    )).build())
                } else {
                    assertTrue(request.toolSpecifications().isNullOrEmpty())
                    val evidence = request.messages().last() as ToolExecutionResultMessage
                    assertEquals("call-1", evidence.id())
                    assertTrue(evidence.text().contains("Connection successful"))
                    assertTrue(!request.messages().toString().contains("synthetic-secret"))
                    handler.onPartialResponse("The check succeeded.")
                    handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("The check succeeded.")).build())
                }
            }
        }
        val events = OllamaChatAgent(model, tool).streamEvents(emptyList()).toList()
        assertEquals(1, executions)
        assertEquals(2, requests)
        assertTrue(events[0] is AgentEvent.ToolCall)
        assertTrue(events[1] is AgentEvent.ToolResult)
        assertEquals(AgentEvent.Text("The check succeeded."), events.last())
    }

    @Test
    fun greetingStreamsActualModelTextWithoutInventingToolActivity() = runTest {
        val model = object : StreamingChatModel {
            override fun doChat(request: ChatRequest, handler: StreamingChatResponseHandler) {
                handler.onPartialResponse("Hello")
                handler.onPartialResponse(" there")
                handler.onCompleteResponse(ChatResponse.builder().aiMessage(AiMessage.from("Hello there")).build())
            }
        }
        val events = OllamaChatAgent(model).streamEvents(emptyList()).toList()
        assertEquals(listOf(AgentEvent.Text("Hello"), AgentEvent.Text(" there")), events)
    }
}

package dev.blaiseagent.state

import dev.blaiseagent.agent.ChatAgent
import dev.blaiseagent.agent.AgentEvent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @Test
    fun toolDurationExcludesModelWaitBeforeAndAfterExecution() = runTest {
        val agent = object : ChatAgent {
            override fun stream(messages: List<ChatMessage>) = flowOf("unused")
            override fun streamEvents(messages: List<ChatMessage>) = flow {
                kotlinx.coroutines.delay(1_000)
                emit(AgentEvent.ToolCall("test_woovi_connection", "call"))
                kotlinx.coroutines.delay(2_000)
                emit(AgentEvent.ToolResult("test_woovi_connection", "Success", "call"))
                kotlinx.coroutines.delay(3_000)
                emit(AgentEvent.Text("Done"))
            }
        }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler), { testScheduler.currentTime }).use { model ->
            model.updateDraft("Check Woovi")
            model.send()
            testScheduler.advanceTimeBy(6_001)
            runCurrent()
            val messages = model.state.value.activeConversation.messages
            assertEquals(2_000L, messages.single { it.status == MessageStatus.ToolCall }.elapsedMillis)
            assertEquals(6_000L, messages.last().elapsedMillis)
        }
    }

    @Test
    fun elapsedTimeUpdatesWhileWaitingAndFreezesAfterCancellation() = runTest {
        val agent = ChatAgent { flow { awaitCancellation() } }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler), { testScheduler.currentTime }).use { model ->
            model.updateDraft("Hello")
            model.send()
            testScheduler.advanceTimeBy(2_000)
            runCurrent()
            assertEquals(2_000L, model.state.value.activeConversation.messages.last().elapsedMillis)
            model.cancelResponse()
            runCurrent()
            testScheduler.advanceTimeBy(3_000)
            runCurrent()
            assertEquals(2_000L, model.state.value.activeConversation.messages.last().elapsedMillis)
            assertEquals(MessageStatus.Cancelled, model.state.value.activeConversation.messages.last().status)
        }
    }

    @Test
    fun unavailableAgentKeepsTheDraftWithoutCreatingMessages() = runTest {
        ChatViewModel(dispatcher = StandardTestDispatcher(testScheduler)).use { model ->
            model.updateDraft("Create a payment link")
            model.send()
            runCurrent()

            assertEquals("Create a payment link", model.state.value.activeConversation.draft)
            assertTrue(model.state.value.activeConversation.messages.isEmpty())
            assertFalse(model.state.value.agentAvailable)
            assertNull(model.state.value.generatingConversationId)
        }
    }

    @Test
    fun blankInputNeverReachesTheAgent() = runTest {
        var calls = 0
        val agent = ChatAgent { calls++; flowOf("answer") }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler)).use { model ->
            model.updateDraft(" \n\t ")
            model.send()
            runCurrent()

            assertEquals(0, calls)
            assertTrue(model.state.value.activeConversation.messages.isEmpty())
        }
    }

    @Test
    fun streamingAppendsToOneReplyAndBlocksDuplicateSubmissions() = runTest {
        val finish = CompletableDeferred<Unit>()
        var calls = 0
        val agent = ChatAgent {
            calls++
            flow {
                emit("Hello")
                finish.await()
                emit(" there")
            }
        }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler)).use { model ->
            model.updateDraft("  Hi  ")
            model.send()
            model.updateDraft("next draft")
            model.send()
            runCurrent()

            val conversation = model.state.value.activeConversation
            assertEquals(1, calls)
            assertEquals(listOf("Hi", "Hello"), conversation.messages.map { it.text })
            assertEquals(MessageStatus.Streaming, conversation.messages.last().status)
            assertEquals(conversation.id, model.state.value.generatingConversationId)
            assertEquals("next draft", conversation.draft)

            finish.complete(Unit)
            runCurrent()
            assertEquals("Hello there", model.state.value.activeConversation.messages.last().text)
            assertEquals(MessageStatus.Complete, model.state.value.activeConversation.messages.last().status)
            assertNull(model.state.value.generatingConversationId)
        }
    }

    @Test
    fun switchingChatsDoesNotRedirectAStreamingReplyOrLoseDrafts() = runTest {
        val finish = CompletableDeferred<Unit>()
        val agent = ChatAgent { flow { finish.await(); emit("First reply") } }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler)).use { model ->
            val firstId = model.state.value.activeConversation.id
            model.updateDraft("First question")
            model.send()
            runCurrent()
            model.newConversation()
            val secondId = model.state.value.activeConversation.id
            model.updateDraft("Second draft")
            finish.complete(Unit)
            runCurrent()

            assertNotEquals(firstId, secondId)
            assertTrue(model.state.value.activeConversation.messages.isEmpty())
            model.selectConversation(firstId)
            assertEquals("First reply", model.state.value.activeConversation.messages.last().text)
            model.selectConversation(secondId)
            assertEquals("Second draft", model.state.value.activeConversation.draft)
            model.selectConversation("missing")
            assertEquals(secondId, model.state.value.activeConversation.id)
        }
    }

    @Test
    fun agentReceivesOnlyTheSelectedConversationsCompletedHistory() = runTest {
        val requests = mutableListOf<List<ChatMessage>>()
        val agent = ChatAgent { messages -> requests.add(messages); flowOf("Answer") }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler)).use { model ->
            model.updateDraft("First question")
            model.send()
            runCurrent()
            model.updateDraft("Follow up")
            model.send()
            runCurrent()
            model.newConversation()
            model.updateDraft("Separate question")
            model.send()
            runCurrent()

            assertEquals(listOf("First question", "Answer", "Follow up"), requests[1].map { it.text })
            assertEquals(listOf("Separate question"), requests[2].map { it.text })
        }
    }

    @Test
    fun cancellationKeepsPartialTextAndAllowsAnotherRequest() = runTest {
        val agent = ChatAgent { flow { emit("Partial"); awaitCancellation() } }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler)).use { model ->
            model.updateDraft("Question")
            model.send()
            runCurrent()
            model.cancelResponse()
            runCurrent()

            val reply = model.state.value.activeConversation.messages.last()
            assertEquals("Partial", reply.text)
            assertEquals(MessageStatus.Cancelled, reply.status)
            assertNull(model.state.value.generatingConversationId)

            model.updateDraft("Another question")
            model.send()
            runCurrent()
            assertEquals(4, model.state.value.activeConversation.messages.size)
        }
    }

    @Test
    fun failuresDoNotExposeExceptionDetailsAndDoNotEnterFutureContext() = runTest {
        var calls = 0
        var nextContext = emptyList<ChatMessage>()
        val agent = ChatAgent { messages ->
            if (calls++ == 0) flow { emit("Incomplete"); error("private diagnostic") }
            else { nextContext = messages; flowOf("Recovered") }
        }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler)).use { model ->
            model.updateDraft("Question")
            model.send()
            runCurrent()
            val reply = model.state.value.activeConversation.messages.last()
            assertEquals(MessageStatus.Failed, reply.status)
            assertFalse(model.state.value.toString().contains("private diagnostic"))
            assertNull(model.state.value.generatingConversationId)

            model.updateDraft("Try again")
            model.send()
            runCurrent()
            assertEquals(listOf("Question", "Try again"), nextContext.map { it.text })
            assertEquals("Recovered", model.state.value.activeConversation.messages.last().text)
        }
    }

    @Test
    fun closingTheOwnerCancelsTheAgent() = runTest {
        var stopped = false
        val agent = ChatAgent {
            flow {
                try { awaitCancellation() } finally { stopped = true }
            }
        }
        val model = ChatViewModel(agent, StandardTestDispatcher(testScheduler))
        model.updateDraft("Question")
        model.send()
        runCurrent()
        model.close()
        runCurrent()

        assertTrue(stopped)
        assertNull(model.state.value.generatingConversationId)
    }

    @Test
    fun toolActivityAppearsBeforeTheFinalAssistantReply() = runTest {
        val agent = object : ChatAgent {
            override fun stream(messages: List<ChatMessage>) = flowOf("unused")

            override fun streamEvents(messages: List<ChatMessage>) = flowOf(
                AgentEvent.ToolCall("test_woovi_connection"),
                AgentEvent.ToolResult("test_woovi_connection", "Sandbox Woovi connection successful."),
                AgentEvent.Text("Connection is ready."),
            )
        }
        ChatViewModel(agent, StandardTestDispatcher(testScheduler)).use { model ->
            model.updateDraft("Test my Woovi connection")
            model.send()
            runCurrent()

            val messages = model.state.value.activeConversation.messages
            assertEquals(listOf(MessageRole.User, MessageRole.Assistant, MessageRole.Assistant, MessageRole.Assistant), messages.map { it.role })
            assertEquals(MessageStatus.ToolCall, messages[1].status)
            assertEquals("Calling test_woovi_connection…", messages[1].text)
            assertEquals(MessageStatus.ToolResult, messages[2].status)
            assertEquals("Sandbox Woovi connection successful.", messages[2].text)
            assertEquals("Connection is ready.", messages[3].text)
        }
    }
}

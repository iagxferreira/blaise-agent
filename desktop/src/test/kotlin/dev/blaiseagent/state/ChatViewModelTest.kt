package dev.blaiseagent.state

import dev.blaiseagent.agent.ChatAgent
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
}

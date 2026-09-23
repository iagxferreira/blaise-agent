package dev.blaiseagent.state

import dev.blaiseagent.agent.ChatAgent
import dev.blaiseagent.agent.AgentEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** UI-owned state. Call actions on the UI dispatcher and close with the window. */
class ChatViewModel(
    agent: ChatAgent? = null,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
) : AutoCloseable {
    private var agent: ChatAgent? = agent
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val initialConversation = Conversation(UUID.randomUUID().toString())
    private val mutableState = MutableStateFlow(
        ChatState(listOf(initialConversation), initialConversation.id, agent != null),
    )
    val state = mutableState.asStateFlow()
    private var responseJob: Job? = null
    private var closed = false

    fun updateDraft(text: String) {
        updateConversation(state.value.activeConversationId) { it.copy(draft = text) }
    }

    fun setAgent(agent: ChatAgent?) {
        this.agent = agent
        mutableState.update { it.copy(agentAvailable = agent != null) }
    }

    fun send() {
        val currentAgent = agent ?: return
        val snapshot = state.value
        val conversation = snapshot.activeConversation
        val text = conversation.draft.trim()
        if (closed || text.isBlank() || snapshot.generatingConversationId != null) return

        val userMessage = ChatMessage(UUID.randomUUID().toString(), MessageRole.User, text)
        val reply = ChatMessage(UUID.randomUUID().toString(), MessageRole.Assistant, "", MessageStatus.Streaming)
        val context = conversation.messages.filter { it.status == MessageStatus.Complete } + userMessage
        updateConversation(conversation.id) {
            it.copy(
                title = if (it.messages.isEmpty()) text.lineSequence().first().take(48) else it.title,
                draft = "",
                messages = it.messages + userMessage + reply,
            )
        }
        mutableState.update { it.copy(generatingConversationId = conversation.id) }

        // Enter the try/finally before returning, so immediate cancellation also cleans up state.
        responseJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentAgent.streamEvents(context).collect { event ->
                    when (event) {
                        is AgentEvent.Text -> updateMessage(conversation.id, reply.id) {
                            it.copy(text = it.text + event.value)
                        }
                        is AgentEvent.ToolCall -> insertBeforeMessage(
                            conversation.id,
                            reply.id,
                            ChatMessage(UUID.randomUUID().toString(), MessageRole.Assistant, "Calling ${event.name}…", MessageStatus.ToolCall),
                        )
                        is AgentEvent.ToolResult -> insertBeforeMessage(
                            conversation.id,
                            reply.id,
                            ChatMessage(UUID.randomUUID().toString(), MessageRole.Assistant, event.value, MessageStatus.ToolResult),
                        )
                    }
                }
                updateMessage(conversation.id, reply.id) {
                    it.copy(status = if (it.text.isBlank()) MessageStatus.Failed else MessageStatus.Complete)
                }
            } catch (cancelled: CancellationException) {
                updateMessage(conversation.id, reply.id) { it.copy(status = MessageStatus.Cancelled) }
                throw cancelled
            } catch (_: Exception) {
                // Transport exceptions can contain request details; UI state gets only a typed failure.
                updateMessage(conversation.id, reply.id) { it.copy(status = MessageStatus.Failed) }
            } finally {
                mutableState.update { it.copy(generatingConversationId = null) }
            }
        }
    }

    fun newConversation() {
        val conversation = Conversation(UUID.randomUUID().toString())
        mutableState.update {
            it.copy(conversations = listOf(conversation) + it.conversations, activeConversationId = conversation.id)
        }
    }

    fun selectConversation(id: String) {
        mutableState.update { current ->
            if (current.conversations.any { it.id == id }) current.copy(activeConversationId = id) else current
        }
    }

    fun cancelResponse() {
        responseJob?.cancel()
    }

    override fun close() {
        closed = true
        scope.cancel()
    }

    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        mutableState.update { current ->
            current.copy(conversations = current.conversations.map { if (it.id == id) transform(it) else it })
        }
    }

    private fun updateMessage(conversationId: String, messageId: String, transform: (ChatMessage) -> ChatMessage) {
        updateConversation(conversationId) { conversation ->
            conversation.copy(messages = conversation.messages.map { if (it.id == messageId) transform(it) else it })
        }
    }

    private fun insertBeforeMessage(conversationId: String, messageId: String, message: ChatMessage) {
        updateConversation(conversationId) { conversation ->
            val index = conversation.messages.indexOfFirst { it.id == messageId }
            if (index < 0) conversation
            else conversation.copy(messages = conversation.messages.toMutableList().apply { add(index, message) })
        }
    }
}

package dev.blaiseagent.state

enum class MessageRole { User, Assistant }
enum class MessageStatus { Streaming, Complete, Cancelled, Failed }

data class ChatMessage(
    val id: String,
    val role: MessageRole,
    val text: String,
    val status: MessageStatus = MessageStatus.Complete,
)

data class Conversation(
    val id: String,
    val title: String = "New conversation",
    val draft: String = "",
    val messages: List<ChatMessage> = emptyList(),
)

data class ChatState(
    val conversations: List<Conversation>,
    val activeConversationId: String,
    val agentAvailable: Boolean,
    val generatingConversationId: String? = null,
) {
    val activeConversation: Conversation get() = conversations.first { it.id == activeConversationId }
}

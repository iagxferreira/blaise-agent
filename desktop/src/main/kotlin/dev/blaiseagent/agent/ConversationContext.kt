package dev.blaiseagent.agent

import dev.blaiseagent.state.ChatMessage
import dev.blaiseagent.state.MessageRole
import dev.blaiseagent.state.MessageStatus

/** Character budget is deterministic, not a model-specific token estimate. */
object ConversationContext {
    fun select(messages: List<ChatMessage>, maxCharacters: Int = 24_000): List<ChatMessage> {
        require(maxCharacters > 0)
        val turns = mutableListOf<MutableList<ChatMessage>>()
        messages.forEach { message ->
            if (message.role == MessageRole.User) turns.add(mutableListOf())
            if (message.status in setOf(MessageStatus.Complete, MessageStatus.ToolCall, MessageStatus.ToolResult)) {
                turns.lastOrNull()?.add(message)
            }
        }
        val selected = mutableListOf<List<ChatMessage>>()
        var size = 0
        for (turn in turns.asReversed()) {
            val length = turn.sumOf { it.text.length }
            // Never silently truncate the user's latest request.
            if (selected.isNotEmpty() && size + length > maxCharacters) break
            selected.add(turn)
            size += length
        }
        return selected.asReversed().flatten()
    }
}

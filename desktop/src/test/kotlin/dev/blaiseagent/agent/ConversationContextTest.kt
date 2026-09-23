package dev.blaiseagent.agent

import dev.blaiseagent.state.ChatMessage
import dev.blaiseagent.state.MessageRole
import dev.blaiseagent.state.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationContextTest {
    @Test
    fun retainsEvidenceAndDropsIncompleteAssistantText() {
        val history = listOf(
            message("1", "Check Woovi", MessageRole.User),
            message("2", "Calling", status = MessageStatus.ToolCall),
            message("3", "Sandbox succeeded", status = MessageStatus.ToolResult),
            message("4", "partial answer", status = MessageStatus.Cancelled),
        )
        assertEquals(listOf("1", "2", "3"), ConversationContext.select(history).map { it.id })
    }

    @Test
    fun evictsWholeOldTurnsInsteadOfSeparatingToolResults() {
        val history = listOf(
            message("1", "old", MessageRole.User),
            message("2", "old result", status = MessageStatus.ToolResult),
            message("3", "new", MessageRole.User),
            message("4", "answer"),
        )
        assertEquals(listOf("3", "4"), ConversationContext.select(history, maxCharacters = 12).map { it.id })
    }

    private fun message(id: String, text: String, role: MessageRole = MessageRole.Assistant,
        status: MessageStatus = MessageStatus.Complete) = ChatMessage(id, role, text, status)
}

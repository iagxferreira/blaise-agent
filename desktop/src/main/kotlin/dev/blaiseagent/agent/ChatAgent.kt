package dev.blaiseagent.agent

import dev.blaiseagent.state.ChatMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Implementations emit text deltas and keep blocking inference off the UI thread. */
fun interface ChatAgent {
    fun stream(messages: List<ChatMessage>): Flow<String>

    fun streamEvents(messages: List<ChatMessage>): Flow<AgentEvent> =
        stream(messages).map { AgentEvent.Text(it) }
}

sealed interface AgentEvent {
    data class Text(val value: String) : AgentEvent
    data class ToolCall(val name: String) : AgentEvent
    data class ToolResult(val name: String, val value: String) : AgentEvent
}

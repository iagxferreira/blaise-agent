package dev.blaiseagent.agent

import dev.blaiseagent.state.ChatMessage
import kotlinx.coroutines.flow.Flow

/** Implementations emit text deltas and keep blocking inference off the UI thread. */
fun interface ChatAgent {
    fun stream(messages: List<ChatMessage>): Flow<String>
}

package dev.blaiseagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import dev.blaiseagent.agent.ConversationContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaiseagent.state.ChatMessage
import dev.blaiseagent.state.ChatState
import dev.blaiseagent.state.MessageRole
import dev.blaiseagent.state.MessageStatus
import dev.blaiseagent.ui.theme.Accent
import dev.blaiseagent.ui.theme.Border
import dev.blaiseagent.ui.theme.Muted
import dev.blaiseagent.ui.theme.Raised

@Composable
fun ChatScreen(
    state: ChatState,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    onSettings: () -> Unit,
) {
    val conversation = state.activeConversation
    val canSend = state.agentAvailable && state.generatingConversationId == null && conversation.draft.isNotBlank()
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (conversation.messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Welcome(onDraft, Modifier.widthIn(max = 780.dp).verticalScroll(rememberScrollState()).padding(32.dp))
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(conversation.id, conversation.messages.lastOrNull()) {
                if (conversation.messages.isNotEmpty()) listState.animateScrollToItem(conversation.messages.lastIndex)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 28.dp),
            ) {
                items(conversation.messages, key = { it.id }) { message ->
                    val result = if (message.status == MessageStatus.ToolCall && message.toolCallId != null) {
                        conversation.messages.firstOrNull {
                            it.status == MessageStatus.ToolResult && it.toolCallId == message.toolCallId
                        }
                    } else null
                    val pairedResult = message.status == MessageStatus.ToolResult && message.toolCallId != null &&
                        conversation.messages.any { it.status == MessageStatus.ToolCall && it.toolCallId == message.toolCallId }
                    when {
                        message.status == MessageStatus.ToolCall -> ToolActivityCard(
                            message, result, state.generatingConversationId == conversation.id &&
                                conversation.messages.indexOf(message) > conversation.messages.indexOfLast { it.role == MessageRole.User },
                        )
                        !pairedResult -> Message(message)
                    }
                }
            }
        }
        Column(Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(horizontal = 32.dp, vertical = 20.dp)) {
            ContextDisclosure(conversation.messages, conversation.draft)
            Surface(shape = RoundedCornerShape(16.dp), color = Raised, border = BorderStroke(1.dp, Border)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    BasicTextField(
                        value = conversation.draft,
                        onValueChange = onDraft,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(Accent),
                        minLines = 2,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Message" }
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Enter && !event.isShiftPressed) {
                                    if (event.type == KeyEventType.KeyDown && canSend) onSend()
                                    true
                                } else false
                            },
                        decorationBox = { input ->
                            Box {
                                if (conversation.draft.isEmpty()) Text("Describe what you’d like to do…", color = Muted, fontSize = 14.sp)
                                input()
                            }
                        },
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        ActionButton(if (state.agentAvailable) "Local model" else "Connect model", onSettings,
                            icon = Icons.Outlined.Computer, style = ControlStyle.Ghost)
                        Spacer(Modifier.weight(1f))
                        if (state.generatingConversationId != null) {
                            ActionButton("Stop", onCancel, icon = Icons.Outlined.Stop)
                        } else {
                            ActionButton("Send", onSend, enabled = canSend, icon = Icons.Outlined.ArrowUpward, style = ControlStyle.Primary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Session only · Chats are not saved yet. " +
                    if (state.agentAvailable) "Shift+Enter for a new line." else "Sending unlocks when Ollama is connected.",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ContextDisclosure(messages: List<ChatMessage>, draft: String) {
    var expanded by remember { mutableStateOf(false) }
    val candidate = if (draft.isBlank()) messages else messages +
        ChatMessage("context-preview", MessageRole.User, draft.trim())
    val included = ConversationContext.select(candidate)
    val turns = included.count { it.role == MessageRole.User }
    val characters = included.sumOf { it.text.length }
    val excluded = candidate.count { it.role == MessageRole.User } - turns
    TextButton(onClick = { expanded = !expanded }) {
        Text("Context: $turns turns · $characters / 24,000 chars ${if (expanded) "▴" else "▾"}", fontSize = 12.sp)
    }
    if (expanded) {
        Surface(color = Raised, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Context for next request", fontWeight = FontWeight.Medium)
                Text("$turns included turns · ${included.count { it.status == MessageStatus.ToolResult }} tool results", fontSize = 12.sp)
                Text("$excluded older turns not sent to model", color = Muted, fontSize = 12.sp)
                Text("Cancelled and failed assistant text is excluded. History remains visible in this session.", color = Muted, fontSize = 12.sp)
                if (characters > 24_000) Text("Latest turn exceeds the history budget; it will be retained whole.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Text("History characters only; system instructions and tool schemas are additional.", color = Muted, fontSize = 12.sp)
                Text("Session only · Global preferences are not implemented", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ToolActivityCard(call: ChatMessage, result: ChatMessage?, generating: Boolean) {
    var expanded by remember(call.id) { mutableStateOf(false) }
    val status = when {
        result != null -> "Result received"
        generating -> "Running"
        else -> "Stopped · no result recorded"
    }
    Surface(color = Raised, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("TOOL · $status${call.elapsedMillis?.let { " · ${formatElapsed(it)}" }.orEmpty()}", color = Muted, fontSize = 10.sp)
            TextButton(onClick = { expanded = !expanded }) {
                Text("${call.toolName ?: "Tool activity"} ${if (expanded) "▴" else "▾"}")
            }
            if (expanded) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(result?.text ?: "No result available yet.", fontSize = 13.sp, lineHeight = 20.sp)
                }
            }
        }
    }
}

@Composable
private fun Welcome(onDraft: (String) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BrandMark()
        Text("LOCAL INTELLIGENCE. CLEAR INTENT.", color = Accent, fontSize = 10.sp, letterSpacing = 2.sp)
        Text("A clearer way to\nmanage payments.", fontSize = 38.sp, lineHeight = 46.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Chat with your local model. Woovi payment operations are coming next.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        )
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Suggestion("Create a payment link", "A simpler way to collect", "Create a Pix payment link for R$150 for consulting.", onDraft, Modifier.weight(1f))
            Suggestion("Check a payment", "Keep track of a charge", "Has my latest charge been paid?", onDraft, Modifier.weight(1f))
        }
        Text("Example drafts · Payment operations are not connected yet", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun Suggestion(title: String, subtitle: String, prompt: String, onDraft: (String) -> Unit, modifier: Modifier) {
    ControlSurface(
        onClick = { onDraft(prompt) },
        modifier = modifier,
        style = ControlStyle.Secondary,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun Message(message: ChatMessage) {
    var showDiagnostics by remember(message.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (message.status) {
                MessageStatus.ToolCall, MessageStatus.ToolResult -> "TOOL"
                else -> if (message.applicationNotice != null) "APP" else if (message.role == MessageRole.User) "YOU" else "BLAISE"
            },
            color = if (message.status == MessageStatus.ToolResult) Accent else Muted,
            fontSize = 10.sp,
            letterSpacing = 1.sp,
        )
        message.applicationNotice?.let { notice ->
            Text(notice, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { showDiagnostics = !showDiagnostics }) {
                Text(if (showDiagnostics) "Hide model output" else "Show model output")
            }
        }
        if (message.text.isNotEmpty() && (message.applicationNotice == null || showDiagnostics)) {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(message.text, fontSize = 15.sp, lineHeight = 24.sp)
            }
        }
        when (message.status) {
            MessageStatus.Streaming -> Text("${if (message.text.isEmpty()) "Waiting for model / tools" else "Responding"} · ${formatElapsed(message.elapsedMillis ?: 0)}", color = Muted, fontSize = 12.sp)
            MessageStatus.ToolCall -> Text("Running securely…", color = Muted, fontSize = 12.sp)
            MessageStatus.ToolResult -> Unit
            MessageStatus.Cancelled -> Text("Response stopped", color = Muted, fontSize = 12.sp)
            MessageStatus.Failed -> if (message.applicationNotice == null) Text("Couldn’t finish this response. Please try again.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            MessageStatus.Complete -> Unit
        }
        if (message.status != MessageStatus.Streaming && message.elapsedMillis != null) {
            Text("Total elapsed · ${formatElapsed(message.elapsedMillis)}", color = Muted, fontSize = 12.sp)
        }
    }
}

private fun formatElapsed(milliseconds: Long): String {
    val minutes = milliseconds / 60_000
    val seconds = (milliseconds / 1_000) % 60
    val millis = milliseconds % 1_000
    return when {
        minutes > 0 -> "${minutes}m ${seconds}s ${millis}ms"
        milliseconds >= 1_000 -> "${seconds}s ${millis}ms"
        else -> "${millis}ms"
    }
}

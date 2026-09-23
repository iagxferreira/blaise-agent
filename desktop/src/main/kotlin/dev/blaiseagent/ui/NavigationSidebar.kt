package dev.blaiseagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaiseagent.state.ChatState
import dev.blaiseagent.ui.theme.Accent
import dev.blaiseagent.ui.theme.Muted
import dev.blaiseagent.ui.theme.Sidebar

@Composable
internal fun NavigationSidebar(
    state: ChatState,
    selectedModel: String?,
    checking: Boolean,
    collapsed: Boolean,
    settingsOpen: Boolean,
    onToggle: () -> Unit,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onConversations: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier.width(if (collapsed) 72.dp else 248.dp).fillMaxHeight().background(Sidebar)
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (collapsed) {
            BrandMark()
            Spacer(Modifier.height(16.dp))
            ActionButton("Expand sidebar", onToggle, icon = Icons.Outlined.ChevronRight, style = ControlStyle.Ghost, iconOnly = true)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark()
                Column(Modifier.weight(1f)) {
                    Text("Blaise", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Local workspace", fontSize = 11.sp, color = Muted)
                }
                ActionButton("Collapse sidebar", onToggle, icon = Icons.Outlined.ChevronLeft, style = ControlStyle.Ghost, iconOnly = true)
            }
        }
        Spacer(Modifier.height(24.dp))
        ActionButton(
            "New chat", onNewChat, modifier = if (collapsed) Modifier else Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Add, style = ControlStyle.Primary, iconOnly = collapsed,
        )
        Spacer(Modifier.height(24.dp))
        if (collapsed) {
            ActionButton("Conversations", onConversations, icon = Icons.Outlined.ChatBubbleOutline,
                style = ControlStyle.Ghost, selected = !settingsOpen, iconOnly = true)
            Spacer(Modifier.weight(1f))
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text("CONVERSATIONS", fontSize = 10.sp, letterSpacing = 1.sp, color = Muted, modifier = Modifier.weight(1f))
                Text("${state.conversations.size}", fontSize = 10.sp, color = Muted)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(state.conversations, key = { it.id }) { conversation ->
                    ControlSurface(
                        onClick = { onSelectChat(conversation.id) },
                        selected = !settingsOpen && conversation.id == state.activeConversationId,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ChatBubbleOutline, null, modifier = Modifier.size(18.dp))
                            Text(conversation.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))
        val status = when {
            checking -> "Checking Ollama…"
            state.agentAvailable -> "Ollama connected"
            else -> "Ollama unavailable"
        }
        val statusColor = when {
            checking -> Muted
            state.agentAvailable -> Accent
            else -> Color(0xFFD4B37A)
        }
        val detail = if (state.agentAvailable) selectedModel ?: "Local inference" else "Open Settings to reconnect"
        ControlTooltip("$status · $detail") {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Outlined.Computer, contentDescription = status, tint = statusColor, modifier = Modifier.size(18.dp))
                if (!collapsed) Column(Modifier.width(178.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(status, color = statusColor, fontSize = 12.sp)
                    Text(detail, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        ActionButton("Settings", onSettings, modifier = if (collapsed) Modifier else Modifier.fillMaxWidth(),
            icon = Icons.Outlined.Settings, style = ControlStyle.Ghost, selected = settingsOpen, iconOnly = collapsed)
        if (!collapsed) Text("Session only · v0.1", fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 16.dp))
    }
}

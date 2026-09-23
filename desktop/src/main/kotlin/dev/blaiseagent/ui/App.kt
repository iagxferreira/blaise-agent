package dev.blaiseagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaiseagent.state.ChatState
import dev.blaiseagent.state.ChatViewModel
import dev.blaiseagent.agent.OllamaChatAgent
import dev.blaiseagent.agent.OllamaClient
import dev.blaiseagent.agent.OllamaConnection
import dev.blaiseagent.agent.OllamaModel
import dev.blaiseagent.ui.theme.Accent
import dev.blaiseagent.ui.theme.Background
import dev.blaiseagent.ui.theme.BlaiseTheme
import dev.blaiseagent.ui.theme.Border
import dev.blaiseagent.ui.theme.Muted
import dev.blaiseagent.ui.theme.Raised
import dev.blaiseagent.ui.theme.Sidebar
import kotlinx.coroutines.launch

@Composable
fun App(model: ChatViewModel, ollamaClient: OllamaClient) {
    val state by model.state.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var ollamaConnection by remember { mutableStateOf<OllamaConnection?>(null) }
    var models by remember { mutableStateOf<List<OllamaModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun refreshOllama() {
        val connection = ollamaClient.checkConnection()
        ollamaConnection = connection
        when (connection) {
            OllamaConnection.Ready -> {
                models = runCatching { ollamaClient.listModels() }.getOrDefault(emptyList())
                val availableModel = models.firstOrNull { it.name == selectedModel } ?: models.firstOrNull()
                selectedModel = availableModel?.name
                if (availableModel == null) {
                    model.setAgent(null)
                    snackbarHostState.showSnackbar("Ollama is running, but no local models are installed")
                } else {
                    model.setAgent(OllamaChatAgent(ollamaClient.endpoint, availableModel.name))
                    snackbarHostState.showSnackbar("Ollama is running · ${models.size} model${if (models.size == 1) "" else "s"} available")
                }
            }
            is OllamaConnection.Unavailable -> {
                model.setAgent(null)
                snackbarHostState.showSnackbar("Ollama is not running · start it and restart or retry")
            }
        }
    }

    fun selectModel(name: String) {
        if (state.generatingConversationId != null) return
        selectedModel = name
        model.setAgent(OllamaChatAgent(ollamaClient.endpoint, name))
    }

    LaunchedEffect(ollamaClient) { refreshOllama() }

    BlaiseTheme {
        Box(Modifier.fillMaxSize().background(Background)) {
            Surface(Modifier.fillMaxSize(), color = Background) {
                Row {
                Sidebar(
                    state = state,
                    selectedModel = selectedModel,
                    settingsOpen = settingsOpen,
                    onNewChat = { model.newConversation(); settingsOpen = false },
                    onSelectChat = { model.selectConversation(it); settingsOpen = false },
                    onSettings = { settingsOpen = true },
                )
                VerticalDivider(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth().height(76.dp).padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (settingsOpen) "Settings" else state.activeConversation.title,
                            modifier = Modifier.weight(1f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        StatusLabel("DEVELOPMENT PREVIEW")
                    }
                    HorizontalDivider()
                    if (settingsOpen) {
                        SettingsScreen(
                            connection = ollamaConnection,
                            endpoint = ollamaClient.endpoint,
                            models = models,
                            selectedModel = selectedModel,
                            canSwitchModel = state.generatingConversationId == null,
                            onSelectModel = ::selectModel,
                            onRefresh = { scope.launch { refreshOllama() } },
                            onBack = { settingsOpen = false },
                        )
                    } else {
                        ChatScreen(state, model::updateDraft, model::send, model::cancelResponse) {
                            settingsOpen = true
                        }
                    }
                }
            }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            )
        }
    }
}

@Composable
private fun Sidebar(
    state: ChatState,
    selectedModel: String?,
    settingsOpen: Boolean,
    onNewChat: () -> Unit,
    onSelectChat: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Column(Modifier.width(248.dp).fillMaxHeight().background(Sidebar).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 10.dp)) {
            BrandMark()
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Blaise", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Your financial copilot", color = Muted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onNewChat,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
        ) {
            Text("+", fontSize = 20.sp)
            Spacer(Modifier.width(10.dp))
            Text("New chat")
        }
        Spacer(Modifier.height(30.dp))
        Text("THIS SESSION", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(state.conversations, key = { it.id }) { conversation ->
                Surface(
                    onClick = { onSelectChat(conversation.id) },
                    color = if (!settingsOpen && conversation.id == state.activeConversationId) Raised else Sidebar,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        conversation.title,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Surface(color = Raised, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    if (state.agentAvailable) "Connected to local AI" else "Local AI unavailable",
                    color = Accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    selectedModel ?: "Start Ollama to enable chat.",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onSettings, modifier = Modifier.fillMaxWidth()) {
            Text("Settings", color = if (settingsOpen) Accent else MaterialTheme.colorScheme.onSurface)
        }
        Text("v0.1 · Desktop foundation", color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
internal fun BrandMark(modifier: Modifier = Modifier) {
    Surface(modifier.size(40.dp), color = Accent, shape = RoundedCornerShape(12.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Text("B", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Background)
        }
    }
}

@Composable
internal fun StatusLabel(text: String) {
    Surface(color = Raised, shape = RoundedCornerShape(6.dp)) {
        Text(text, color = Muted, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(8.dp))
    }
}

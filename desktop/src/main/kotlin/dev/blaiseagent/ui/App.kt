package dev.blaiseagent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaiseagent.agent.OllamaChatAgent
import dev.blaiseagent.agent.OllamaClient
import dev.blaiseagent.agent.OllamaConnection
import dev.blaiseagent.agent.OllamaModel
import dev.blaiseagent.state.ChatViewModel
import dev.blaiseagent.ui.theme.Accent
import dev.blaiseagent.ui.theme.Background
import dev.blaiseagent.ui.theme.BlaiseTheme
import dev.blaiseagent.ui.theme.Muted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun App(model: ChatViewModel, ollamaClient: OllamaClient) {
    val state by model.state.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var collapsed by remember { mutableStateOf(false) }
    var ollamaConnection by remember { mutableStateOf<OllamaConnection?>(null) }
    var models by remember { mutableStateOf<List<OllamaModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    suspend fun refreshOllama() {
        if (refreshing || model.state.value.generatingConversationId != null) return
        refreshing = true
        model.setAgent(null)
        val message = try {
            val connection = ollamaClient.checkConnection()
            ollamaConnection = connection
            when (connection) {
                OllamaConnection.Ready -> {
                    models = ollamaClient.listModels()
                    val availableModel = models.firstOrNull { it.name == selectedModel } ?: models.firstOrNull()
                    selectedModel = availableModel?.name
                    if (availableModel == null) {
                        "Ollama is running, but no local models are installed"
                    } else {
                        model.setAgent(OllamaChatAgent(ollamaClient.endpoint, availableModel.name))
                        "Ollama is running · ${models.size} model${if (models.size == 1) "" else "s"} available"
                    }
                }
                is OllamaConnection.Unavailable -> {
                    models = emptyList()
                    selectedModel = null
                    "Ollama is unreachable · start it and use Refresh in Settings"
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            models = emptyList()
            selectedModel = null
            ollamaConnection = OllamaConnection.Unavailable("Could not load Ollama models. Try Refresh.")
            "Could not load Ollama models. Try Refresh in Settings."
        } finally {
            refreshing = false
        }
        snackbarHostState.showSnackbar(message)
    }

    fun selectModel(name: String) {
        if (refreshing || model.state.value.generatingConversationId != null) return
        selectedModel = name
        model.setAgent(OllamaChatAgent(ollamaClient.endpoint, name))
    }

    LaunchedEffect(ollamaClient) { refreshOllama() }

    BlaiseTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
        Box(Modifier.fillMaxSize().background(Background)) {
            Row {
                NavigationSidebar(
                    state = state,
                    selectedModel = selectedModel,
                    checking = refreshing || ollamaConnection == null,
                    collapsed = collapsed,
                    settingsOpen = settingsOpen,
                    onToggle = { collapsed = !collapsed },
                    onNewChat = { model.newConversation(); settingsOpen = false },
                    onSelectChat = { model.selectConversation(it); settingsOpen = false },
                    onConversations = { collapsed = false; settingsOpen = false },
                    onSettings = { settingsOpen = true },
                )
                VerticalDivider(Modifier.fillMaxHeight())
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (settingsOpen) "Settings" else state.activeConversation.title,
                            modifier = Modifier.weight(1f).padding(end = 24.dp),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        PreviewLabel()
                    }
                    HorizontalDivider()
                    if (settingsOpen) {
                        SettingsScreen(
                            connection = ollamaConnection,
                            endpoint = ollamaClient.endpoint,
                            models = models,
                            selectedModel = selectedModel,
                            canSwitchModel = !refreshing && state.generatingConversationId == null,
                            refreshing = refreshing,
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
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp))
        }
        }
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
    Text(text, color = Muted, fontSize = 10.sp, letterSpacing = 1.sp)
}

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
import dev.blaiseagent.agent.WooviConnectionTool
import dev.blaiseagent.config.CredentialKey
import dev.blaiseagent.config.CredentialStore
import dev.blaiseagent.config.CredentialStoreFactory
import dev.blaiseagent.config.WooviEnvironment
import dev.blaiseagent.payments.WooviClient
import dev.blaiseagent.payments.WooviConnection
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
    var activeOllamaClient by remember { mutableStateOf(ollamaClient) }
    var ollamaEndpoint by remember { mutableStateOf(ollamaClient.endpoint) }
    var ollamaConnection by remember { mutableStateOf<OllamaConnection?>(null) }
    var models by remember { mutableStateOf<List<OllamaModel>>(emptyList()) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var credentialStore by remember { mutableStateOf<CredentialStore?>(null) }
    var wooviCredentialSaved by remember { mutableStateOf(false) }
    var credentialBusy by remember { mutableStateOf(false) }
    var wooviEnvironment by remember { mutableStateOf(WooviEnvironment.Sandbox) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val wooviConnectionTool = remember(credentialStore) {
        credentialStore?.let { WooviConnectionTool(it, { wooviEnvironment }) }
    }

    fun createAgent(client: OllamaClient, modelName: String): OllamaChatAgent =
        OllamaChatAgent(client.endpoint, modelName, wooviConnectionTool = wooviConnectionTool)

    suspend fun refreshOllama(client: OllamaClient = activeOllamaClient) {
        if (refreshing || model.state.value.generatingConversationId != null) return
        refreshing = true
        model.setAgent(null)
        val message = try {
            val connection = client.checkConnection()
            ollamaConnection = connection
            when (connection) {
                OllamaConnection.Ready -> {
                    models = ollamaClient.listModels()
                    val availableModel = models.firstOrNull { it.name == selectedModel } ?: models.firstOrNull()
                    selectedModel = availableModel?.name
                    if (availableModel == null) {
                        "Ollama is running, but no local models are installed"
                    } else {
                        model.setAgent(createAgent(client, availableModel.name))
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
        model.setAgent(createAgent(activeOllamaClient, name))
    }

    fun testEndpoint(candidate: String) {
        if (refreshing || model.state.value.generatingConversationId != null) return
        scope.launch {
            val normalized = candidate.trim()
            if (normalized.isBlank()) {
                snackbarHostState.showSnackbar("Enter an Ollama endpoint first")
                return@launch
            }
            val candidateClient = runCatching { OllamaClient(normalized) }.getOrNull()
            if (candidateClient == null) {
                snackbarHostState.showSnackbar("That Ollama endpoint is not valid")
                return@launch
            }
            refreshing = true
            try {
                when (candidateClient.checkConnection()) {
                    OllamaConnection.Ready -> {
                        val discovered = candidateClient.listModels()
                        activeOllamaClient = candidateClient
                        ollamaEndpoint = candidateClient.endpoint
                        ollamaConnection = OllamaConnection.Ready
                        models = discovered
                        val availableModel = discovered.firstOrNull { it.name == selectedModel } ?: discovered.firstOrNull()
                        selectedModel = availableModel?.name
                        if (availableModel == null) {
                            model.setAgent(null)
                            snackbarHostState.showSnackbar("Connected, but no local models are installed")
                        } else {
                            model.setAgent(createAgent(candidateClient, availableModel.name))
                            snackbarHostState.showSnackbar("Connection successful · ${discovered.size} model${if (discovered.size == 1) "" else "s"} available")
                        }
                    }
                    is OllamaConnection.Unavailable -> snackbarHostState.showSnackbar("Could not connect to that Ollama endpoint")
                }
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("Connected to Ollama, but model discovery failed")
            } finally {
                refreshing = false
            }
        }
    }

    LaunchedEffect(ollamaClient) { refreshOllama(ollamaClient) }
    LaunchedEffect(credentialStore, selectedModel) {
        if (credentialStore != null && selectedModel != null && ollamaConnection == OllamaConnection.Ready) {
            model.setAgent(createAgent(activeOllamaClient, selectedModel!!))
        }
    }
    LaunchedEffect(Unit) {
        credentialStore = CredentialStoreFactory.create()
        wooviCredentialSaved = runCatching {
            credentialStore?.contains(wooviEnvironment.credentialKey) == true
        }.getOrDefault(false)
    }

    fun selectWooviEnvironment(environment: WooviEnvironment) {
        if (credentialBusy) return
        wooviEnvironment = environment
        scope.launch {
            wooviCredentialSaved = runCatching {
                credentialStore?.contains(environment.credentialKey) == true
            }.getOrDefault(false)
        }
    }

    fun saveWooviKey(value: String) {
        val store = credentialStore
        if (store == null) {
            scope.launch { snackbarHostState.showSnackbar("Secure credential storage is unavailable") }
            return
        }
        scope.launch {
            credentialBusy = true
            try {
                store.write(wooviEnvironment.credentialKey, value)
                wooviCredentialSaved = true
                snackbarHostState.showSnackbar("Woovi ${wooviEnvironment.label.lowercase()} API key saved securely")
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("Could not save the Woovi API key securely")
            } finally {
                credentialBusy = false
            }
        }
    }

    fun removeWooviKey() {
        val store = credentialStore ?: return
        scope.launch {
            credentialBusy = true
            try {
                store.delete(wooviEnvironment.credentialKey)
                wooviCredentialSaved = false
                snackbarHostState.showSnackbar("Woovi ${wooviEnvironment.label.lowercase()} API key removed")
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("Could not remove the Woovi API key")
            } finally {
                credentialBusy = false
            }
        }
    }

    fun testWooviConnection() {
        val store = credentialStore
        if (store == null || credentialBusy) return
        scope.launch {
            credentialBusy = true
            try {
                val key = store.read(wooviEnvironment.credentialKey)
                if (key.isNullOrBlank()) {
                    snackbarHostState.showSnackbar("Save a ${wooviEnvironment.label.lowercase()} Woovi API key first")
                } else {
                    when (WooviClient(wooviEnvironment).testConnection(key)) {
                        WooviConnection.Ready -> snackbarHostState.showSnackbar("Woovi ${wooviEnvironment.label.lowercase()} connection successful")
                        is WooviConnection.Unavailable -> snackbarHostState.showSnackbar("Woovi ${wooviEnvironment.label.lowercase()} connection failed")
                    }
                }
            } catch (_: Exception) {
                snackbarHostState.showSnackbar("Could not read the Woovi API key securely")
            } finally {
                credentialBusy = false
            }
        }
    }

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
                            endpoint = ollamaEndpoint,
                            models = models,
                            selectedModel = selectedModel,
                            canSwitchModel = !refreshing && state.generatingConversationId == null,
                            refreshing = refreshing,
                            onSelectModel = ::selectModel,
                            onEndpointChange = { ollamaEndpoint = it },
                            onTestConnection = { testEndpoint(ollamaEndpoint) },
                            onRefresh = { scope.launch { refreshOllama() } },
                            wooviCredentialSaved = wooviCredentialSaved,
                            wooviEnvironment = wooviEnvironment,
                            credentialAvailable = credentialStore != null,
                            credentialBusy = credentialBusy,
                            onSaveWooviKey = ::saveWooviKey,
                            onRemoveWooviKey = ::removeWooviKey,
                            onSelectWooviEnvironment = ::selectWooviEnvironment,
                            onTestWooviConnection = ::testWooviConnection,
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

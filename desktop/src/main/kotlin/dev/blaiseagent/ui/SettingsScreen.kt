package dev.blaiseagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import dev.blaiseagent.ui.theme.Border
import dev.blaiseagent.ui.theme.Muted
import dev.blaiseagent.ui.theme.Raised
import dev.blaiseagent.agent.OllamaConnection
import dev.blaiseagent.agent.OllamaModel
import dev.blaiseagent.config.WooviEnvironment

@Composable
fun SettingsScreen(
    connection: OllamaConnection?,
    endpoint: String,
    models: List<OllamaModel>,
    selectedModel: String?,
    canSwitchModel: Boolean,
    refreshing: Boolean,
    onEndpointChange: (String) -> Unit,
    onTestConnection: () -> Unit,
    onSelectModel: (String) -> Unit,
    onRefresh: () -> Unit,
    wooviCredentialSaved: Boolean,
    wooviEnvironment: WooviEnvironment,
    credentialAvailable: Boolean,
    credentialBusy: Boolean,
    onSaveWooviKey: (String) -> Unit,
    onRemoveWooviKey: () -> Unit,
    onSelectWooviEnvironment: (WooviEnvironment) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your workspace, your way.", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Manage local models and gateway connections.", color = Muted, fontSize = 14.sp)
            }
            SettingsCard("Ollama", "LOCAL INFERENCE") {
                ConnectionSummary(if (refreshing) null else connection)
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = onEndpointChange,
                    label = { Text("Ollama endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ActionButton(
                        if (refreshing) "Testing…" else "Test connection",
                        onTestConnection,
                        enabled = canSwitchModel,
                        loading = refreshing,
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Installed models", fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    ActionButton(
                        if (refreshing) "Refreshing…" else "Refresh", onRefresh,
                        icon = Icons.Outlined.Refresh, enabled = canSwitchModel, loading = refreshing,
                    )
                }
                if (models.isEmpty()) {
                    Text(
                        when {
                            refreshing -> "Loading installed models…"
                            connection != OllamaConnection.Ready -> "Reconnect to Ollama and refresh the model list."
                            else -> "No local models found. Install one with ollama pull <model>."
                        },
                        color = Muted, fontSize = 13.sp,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        models.forEach { model ->
                            ModelRow(model, model.name == selectedModel, canSwitchModel, onSelectModel)
                        }
                    }
                }
                Text(
                    if (!canSwitchModel && !refreshing) "Model controls are paused until the current response finishes."
                    else "Choose the model for your next request.",
                    color = Muted, fontSize = 13.sp, lineHeight = 21.sp,
                )
            }
            SettingsCard("Woovi", "SANDBOX FIRST") {
                var apiKey by remember { mutableStateOf("") }
                Text("Environment", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WooviEnvironment.entries.forEach { environment ->
                        ActionButton(
                            environment.label,
                            onClick = { onSelectWooviEnvironment(environment) },
                            selected = environment == wooviEnvironment,
                            enabled = !credentialBusy,
                            style = if (environment == WooviEnvironment.Production) ControlStyle.Secondary else ControlStyle.Ghost,
                        )
                    }
                }
                Text(
                    "${wooviEnvironment.baseUrl} · ${if (wooviEnvironment == WooviEnvironment.Sandbox) "Safe for testing" else "Real money environment"}",
                    color = if (wooviEnvironment == WooviEnvironment.Production) MaterialTheme.colorScheme.error else Muted,
                    fontSize = 12.sp,
                )
                Text(
                    if (wooviCredentialSaved) "${wooviEnvironment.label} API key is saved in your OS credential store."
                    else "Save your ${wooviEnvironment.label.lowercase()} Woovi AppID to enable payment operations.",
                    color = Muted,
                    fontSize = 14.sp,
                )
                Text(
                    "Woovi expects the AppID in the Authorization header. This key is never sent to Ollama, stored in chat history, or written to ordinary preferences.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                )
                if (credentialAvailable) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        enabled = !credentialBusy,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(if (wooviCredentialSaved) "Enter a replacement key" else "Woovi ${wooviEnvironment.label.lowercase()} API key") },
                        supportingText = { Text("Stored securely by your operating system") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(
                            if (credentialBusy) "Saving…" else if (wooviCredentialSaved) "Replace key" else "Save key",
                            onClick = {
                                if (apiKey.isNotBlank()) {
                                    onSaveWooviKey(apiKey)
                                    apiKey = ""
                                }
                            },
                            enabled = apiKey.isNotBlank() && !credentialBusy,
                            loading = credentialBusy,
                            style = ControlStyle.Primary,
                        )
                        if (wooviCredentialSaved) {
                            TextButton(onClick = onRemoveWooviKey, enabled = !credentialBusy) { Text("Remove") }
                        }
                    }
                } else {
                    Text("Secure credential storage is unavailable. Use session-only credentials when the gateway adapter is enabled.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
            Text("Conversations and drafts currently live in memory and are cleared when the app closes.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            ActionButton("Back to chat", onBack, icon = Icons.AutoMirrored.Outlined.ArrowBack, style = ControlStyle.Ghost)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ConnectionSummary(connection: OllamaConnection?) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        val connected = connection == OllamaConnection.Ready
        Box(
            Modifier.size(10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                Modifier.size(8.dp),
                color = if (connection == null) Muted else if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                shape = RoundedCornerShape(50),
            ) { }
        }
        Text(
            when (connection) {
                OllamaConnection.Ready -> "Connected · local inference is ready"
                is OllamaConnection.Unavailable -> "Offline · start Ollama to enable chat"
                null -> "Checking Ollama connection…"
            },
            color = Muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun ModelRow(model: OllamaModel, selected: Boolean, canSelect: Boolean, onSelect: (String) -> Unit) {
    ControlSurface(
        onClick = { onSelect(model.name) },
        enabled = canSelect,
        selected = selected,
        style = ControlStyle.Secondary,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(model.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(
                    formatModelMeta(model),
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
            if (selected) Text("ACTIVE", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatModelMeta(model: OllamaModel): String {
    val size = String.format(Locale.ROOT, "%.1f GB", model.sizeBytes / 1_000_000_000.0)
    return listOfNotNull(size, model.family).joinToString(" · ")
}

@Composable
private fun SettingsCard(title: String, badge: String, content: @Composable () -> Unit) {
    Surface(color = Raised, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                StatusLabel(badge)
            }
            content()
        }
    }
}

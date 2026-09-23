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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import dev.blaiseagent.ui.theme.Border
import dev.blaiseagent.ui.theme.Muted
import dev.blaiseagent.ui.theme.Raised
import dev.blaiseagent.agent.OllamaConnection
import dev.blaiseagent.agent.OllamaModel

@Composable
fun SettingsScreen(
    connection: OllamaConnection?,
    endpoint: String,
    models: List<OllamaModel>,
    selectedModel: String?,
    canSwitchModel: Boolean,
    onSelectModel: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 760.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Your workspace, your way.", fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text("Connections and credentials will live here.", color = Muted, fontSize = 14.sp)
            }
            SettingsCard("Ollama", "LOCAL INFERENCE") {
                ConnectionSummary(connection)
                OutlinedTextField(
                    value = endpoint,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Ollama endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Installed models", fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRefresh) { Text("Refresh") }
                }
                if (models.isEmpty()) {
                    Text("No local models found. Install one with `ollama pull <model>`.", color = Muted, fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        models.forEach { model ->
                            ModelRow(model, model.name == selectedModel, canSwitchModel, onSelectModel)
                        }
                    }
                }
                Text("Choose a model for the next request. Active responses continue on their current model.", color = Muted, fontSize = 13.sp, lineHeight = 21.sp)
            }
            SettingsCard("Woovi", "SANDBOX FIRST") {
                Text("Woovi sandbox is not connected yet.", color = Muted, fontSize = 14.sp)
                Text(
                    "Credentials will be stored in your operating system’s secure credential store. Until that integration lands, no API key is requested or saved.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                )
                Text("Planned: save · replace · remove · test connection", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Text("Conversations and drafts currently live in memory and are cleared when the app closes.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            TextButton(onClick = onBack) { Text("← Back to chat") }
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
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
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
    Surface(
        onClick = { if (canSelect) onSelect(model.name) },
        color = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else Border),
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

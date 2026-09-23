package dev.blaiseagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import dev.blaiseagent.ui.theme.Border
import dev.blaiseagent.ui.theme.Muted
import dev.blaiseagent.ui.theme.Raised
import dev.blaiseagent.agent.OllamaConnection
import dev.blaiseagent.agent.OllamaModel

@Composable
fun SettingsScreen(connection: OllamaConnection?, models: List<OllamaModel>, onBack: () -> Unit) {
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
                Text(
                    when (connection) {
                        OllamaConnection.Ready -> "Ollama is running. Models are loaded from its local API."
                        is OllamaConnection.Unavailable -> "Ollama is not reachable. Start it and reopen the app."
                        null -> "Checking Ollama connection…"
                    },
                    color = Muted,
                    fontSize = 14.sp,
                )
                OutlinedTextField(
                    value = "http://127.0.0.1:11434",
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Planned default endpoint") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (models.isNotEmpty()) {
                    Text("Installed models", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    models.forEach { model ->
                        Text(
                            "${model.name} · ${model.sizeBytes / 1_000_000_000.0} GB" +
                                (model.family?.let { " · $it" } ?: ""),
                            color = Muted,
                            fontSize = 13.sp,
                        )
                    }
                }
                Text(
                    "Installed models load directly from Ollama. Model switching and refresh controls arrive with the next agent slice.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                )
            }
            SettingsCard("Woovi", "SANDBOX FIRST") {
                Text("Secure credential storage is not implemented yet.", color = Muted, fontSize = 14.sp)
                Text(
                    "The app does not accept API keys in this preview. Saved keys will use your operating system’s credential store, with session-only use when it’s unavailable.",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                )
                Text("Coming next: save · replace · remove · test connection", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
            }
            Text("Conversations and drafts currently live in memory and are cleared when the app closes.", color = Muted, fontSize = 12.sp, lineHeight = 20.sp)
            TextButton(onClick = onBack) { Text("← Back to chat") }
            Spacer(Modifier.height(8.dp))
        }
    }
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

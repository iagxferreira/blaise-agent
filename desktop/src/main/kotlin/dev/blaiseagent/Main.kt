package dev.blaiseagent

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.blaiseagent.state.ChatViewModel
import dev.blaiseagent.ui.App
import java.awt.Dimension

fun main() = application {
    val model = remember { ChatViewModel() }
    DisposableEffect(model) {
        onDispose { model.close() }
    }
    Window(
        onCloseRequest = ::exitApplication,
        title = "Blaise Agent",
        state = rememberWindowState(width = 1200.dp, height = 820.dp),
    ) {
        DisposableEffect(window) {
            window.minimumSize = Dimension(900, 640)
            onDispose { }
        }
        App(model)
    }
}

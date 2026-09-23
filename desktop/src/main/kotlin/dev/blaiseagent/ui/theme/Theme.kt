package dev.blaiseagent.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Background = Color(0xFF101214)
val Sidebar = Color(0xFF15181B)
val Raised = Color(0xFF1C2024)
val Border = Color(0xFF2B3237)
val Accent = Color(0xFFBBDDAB)
val Muted = Color(0xFF99A3A9)

private val colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF20311B),
    secondary = Accent,
    background = Background,
    surface = Sidebar,
    surfaceVariant = Raised,
    onBackground = Color(0xFFE8EDE9),
    onSurface = Color(0xFFE8EDE9),
    onSurfaceVariant = Muted,
    outline = Border,
    outlineVariant = Border,
    error = Color(0xFFFFB4A9),
)

@Composable
fun BlaiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp),
            large = RoundedCornerShape(18.dp),
        ),
        content = content,
    )
}

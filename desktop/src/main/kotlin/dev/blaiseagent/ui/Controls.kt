package dev.blaiseagent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.blaiseagent.ui.theme.Accent
import dev.blaiseagent.ui.theme.Border
import dev.blaiseagent.ui.theme.Muted
import dev.blaiseagent.ui.theme.Raised

internal enum class ControlStyle { Primary, Secondary, Ghost }

/** Shared pointer, keyboard, selected, and disabled treatment for all desktop controls. */
@Composable
internal fun ControlSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ControlStyle = ControlStyle.Ghost,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val primary = style == ControlStyle.Primary
    val background = when {
        !enabled -> if (primary) Color(0xFF202529) else if (selected) Color(0xFF243127) else Color.Transparent
        primary && pressed -> Color(0xFFA6CA96)
        primary && hovered -> Color(0xFFCBE8BD)
        primary -> Accent
        pressed -> Color(0xFF303940)
        selected -> Color(0xFF243127)
        hovered -> Color(0xFF242A2F)
        style == ControlStyle.Secondary -> Raised
        else -> Color.Transparent
    }
    val foreground = when {
        !enabled -> Color(0xFF819099)
        primary -> Color(0xFF20311B)
        selected -> Accent
        else -> MaterialTheme.colorScheme.onSurface
    }
    val border = when {
        focused && enabled -> BorderStroke(2.dp, Accent)
        selected -> BorderStroke(1.dp, Color(0xFF536D51))
        style == ControlStyle.Secondary -> BorderStroke(1.dp, Border)
        else -> null
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier.semantics { role = Role.Button; this.selected = selected },
        color = background,
        contentColor = foreground,
        border = border,
        shape = RoundedCornerShape(8.dp),
        content = content,
    )
}

@Composable
internal fun ActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    style: ControlStyle = ControlStyle.Secondary,
    enabled: Boolean = true,
    selected: Boolean = false,
    iconOnly: Boolean = false,
    loading: Boolean = false,
) {
    val button: @Composable () -> Unit = {
        ControlSurface(
            onClick = onClick,
            modifier = modifier.then(if (iconOnly) Modifier.size(40.dp) else Modifier.heightIn(min = 40.dp))
                .semantics { contentDescription = label },
            style = style,
            enabled = enabled && !loading,
            selected = selected,
        ) {
            Row(
                Modifier.padding(horizontal = if (iconOnly) 10.dp else 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Muted)
                else if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                if (!iconOnly) Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (iconOnly) ControlTooltip(label, button) else button()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ControlTooltip(label: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Surface(color = Raised, border = BorderStroke(1.dp, Border), shape = RoundedCornerShape(6.dp)) {
                Text(label, modifier = Modifier.padding(10.dp), fontSize = 12.sp)
            }
        },
        delayMillis = 400,
        content = content,
    )
}

/** Release information, deliberately not styled or exposed as a button. */
@Composable
internal fun PreviewLabel() {
    ControlTooltip("Development preview · Chats are session-only. Payment tools are not connected.") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Science, contentDescription = null, tint = Color(0xFFD4B37A), modifier = Modifier.size(16.dp))
            Text("Development preview", color = Muted, fontSize = 11.sp)
        }
    }
}

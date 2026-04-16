package dev.lostf1sh.syncthing.ui.core.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Expressive status pill with a colored dot + label.
 * Uses a surface-backed pill instead of AssistChip to avoid click semantics.
 */
@Composable
fun StatusChip(
    state: String,
    modifier: Modifier = Modifier,
) {
    val (label, color) = stateInfo(state)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(color, shape = CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
        }
    }
}

private fun stateInfo(state: String): Pair<String, Color> = when (state.lowercase()) {
    "idle" -> "Idle" to Color(0xFF059669)
    "syncing" -> "Syncing" to Color(0xFF0891B2)
    "scanning" -> "Scanning" to Color(0xFF7C3AED)
    "error" -> "Error" to Color(0xFFDC2626)
    "paused" -> "Paused" to Color(0xFF6B7280)
    "waiting" -> "Waiting" to Color(0xFFF59E0B)
    "cleaning" -> "Cleaning" to Color(0xFF7C3AED)
    else -> state.replaceFirstChar { it.uppercase() } to Color(0xFF6B7280)
}

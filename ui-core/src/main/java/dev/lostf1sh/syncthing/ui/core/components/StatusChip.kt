package dev.lostf1sh.syncthing.ui.core.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun StatusChip(
    state: String,
    modifier: Modifier = Modifier,
) {
    val (label, color) = stateInfo(state)
    AssistChip(
        onClick = { },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
            )
        },
        modifier = modifier,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.12f),
            labelColor = color,
        ),
    )
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

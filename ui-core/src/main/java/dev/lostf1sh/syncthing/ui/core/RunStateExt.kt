package dev.lostf1sh.syncthing.ui.core

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import dev.lostf1sh.syncthing.native.RunState

fun RunState.displayLabel(): String = when (this) {
    is RunState.Stopped -> "Stopped"
    is RunState.Starting -> "Starting..."
    is RunState.Running -> "Running"
    is RunState.Paused -> "Paused"
    is RunState.Crashed -> "Crashed"
}

fun RunState.displayLabelWithReason(): String = when (this) {
    is RunState.Crashed -> "Crashed: $reason"
    else -> displayLabel()
}

@Composable
fun RunState.displayColor(): Color = when (this) {
    is RunState.Running -> MaterialTheme.colorScheme.primary
    is RunState.Crashed -> MaterialTheme.colorScheme.error
    is RunState.Paused -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

package dev.lostf1sh.syncthing.ui.core.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Semantic status color tokens derived from the active [MaterialTheme.colorScheme].
 *
 * Use these instead of hardcoded hex colors so dark/light/dynamic themes
 * stay coherent and accessibility contrast is maintained.
 */
object StatusTokens {
    /** Healthy / connected / success states. */
    val success: Color @Composable get() = MaterialTheme.colorScheme.primary

    /** Warning / caution states. */
    val warning: Color @Composable get() = MaterialTheme.colorScheme.tertiary

    /** Error / disconnected / failure states. */
    val error: Color @Composable get() = MaterialTheme.colorScheme.error

    /** Neutral / inactive / paused states. */
    val neutral: Color @Composable get() = MaterialTheme.colorScheme.outline

    /** Online / active indicator dot. */
    val online: Color @Composable get() = MaterialTheme.colorScheme.primary

    /** Offline / inactive indicator dot. */
    val offline: Color @Composable get() = MaterialTheme.colorScheme.outline
}

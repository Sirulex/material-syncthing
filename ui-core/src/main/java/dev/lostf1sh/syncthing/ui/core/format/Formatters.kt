package dev.lostf1sh.syncthing.ui.core.format

import java.util.concurrent.TimeUnit

/**
 * Format a byte count into human-readable IEC units (KiB, MiB, GiB, TiB).
 */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes / 1024.0
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    return "%.1f ${units[unitIndex]}".format(value)
}

/**
 * Format a byte count using SI-style units (KB, MB, GB) for UI contexts
 * that prefer decimal prefixes.
 */
fun formatBytesDecimal(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes / 1000.0
    var unitIndex = 0
    while (value >= 1000 && unitIndex < units.lastIndex) {
        value /= 1000.0
        unitIndex++
    }
    return "%.1f ${units[unitIndex]}".format(value)
}

/**
 * Format a relative time span from a timestamp (milliseconds) to a
 * human-readable string like "2 min ago" or "3 hours ago".
 */
fun formatRelativeTime(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = nowMs - timestampMs
    return when {
        diff < 0 -> "in the future"
        diff < TimeUnit.MINUTES.toMillis(1) -> "just now"
        diff < TimeUnit.HOURS.toMillis(1) -> {
            val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
            "$mins min${if (mins > 1) "s" else ""} ago"
        }
        diff < TimeUnit.DAYS.toMillis(1) -> {
            val hours = TimeUnit.MILLISECONDS.toHours(diff)
            "$hours hour${if (hours > 1) "s" else ""} ago"
        }
        diff < TimeUnit.DAYS.toMillis(7) -> {
            val days = TimeUnit.MILLISECONDS.toDays(diff)
            "$days day${if (days > 1) "s" else ""} ago"
        }
        else -> {
            val weeks = TimeUnit.MILLISECONDS.toDays(diff) / 7
            "$weeks week${if (weeks > 1) "s" else ""} ago"
        }
    }
}

/**
 * Map a Syncthing folder/device state string to a user-facing label.
 */
fun stateDisplayLabel(state: String): String = when (state.lowercase()) {
    "idle" -> "Idle"
    "syncing" -> "Syncing"
    "scanning" -> "Scanning"
    "error" -> "Error"
    "paused" -> "Paused"
    "waiting" -> "Waiting"
    "cleaning" -> "Cleaning"
    "unknown" -> "Unknown"
    else -> state.replaceFirstChar { it.uppercase() }
}

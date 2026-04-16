package dev.lostf1sh.syncthing.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class DeviceStats(
    val lastSeen: String = "",
    val lastConnectionDurationS: Double = 0.0,
)

@Serializable
data class FolderStats(
    val lastFile: LastFile = LastFile(),
    val lastScan: String = "",
)

@Serializable
data class LastFile(
    val at: String = "",
    val filename: String = "",
    val deleted: Boolean = false,
)

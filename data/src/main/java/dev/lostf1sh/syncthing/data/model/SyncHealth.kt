package dev.lostf1sh.syncthing.data.model

/**
 * Aggregated health status across all folders and devices.
 */
data class SyncHealth(
    val overall: Status,
    val folderCount: Int,
    val deviceCount: Int,
    val connectedDevices: Int,
    val syncingFolders: Int,
    val errorFolders: Int,
    val pausedFolders: Int,
    val issues: List<SyncIssue>,
) {
    enum class Status { UP_TO_DATE, SCANNING, SYNCING, ERROR, PAUSED, STOPPED }
}

/**
 * Individual issue surfaced in error center or health dashboard.
 */
data class SyncIssue(
    val id: String,
    val type: Type,
    val folderId: String? = null,
    val deviceId: String? = null,
    val message: String,
    val path: String? = null,
    val timestamp: String? = null,
) {
    enum class Type { PULL_ERROR, PERMISSION, DISK_FULL, DEVICE_OFFLINE, SERVICE_CRASH, FOLDER_ERROR }
}

/**
 * Conflict file discovered in a synced folder.
 */
data class ConflictItem(
    val folderId: String,
    val path: String,
    val modifiedLocal: String? = null,
    val modifiedRemote: String? = null,
    val sizeLocal: Long = 0,
    val sizeRemote: Long = 0,
)

/**
 * Single bandwidth sample — byte rates and the wall-clock time they were computed.
 */
data class BandwidthSample(
    val timestamp: Long,
    val inBytesPerSec: Long,
    val outBytesPerSec: Long,
)

/**
 * Sync profile controlling when syncing is allowed.
 */
data class SyncProfile(
    val id: String,
    val label: String,
    val wifiOnly: Boolean = false,
    val chargingOnly: Boolean = false,
    val allowMetered: Boolean = true,
    val respectBatterySaver: Boolean = true,
    val maxSendKbps: Int = 0,
    val maxRecvKbps: Int = 0,
)

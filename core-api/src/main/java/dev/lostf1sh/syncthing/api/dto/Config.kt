package dev.lostf1sh.syncthing.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class Folder(
    val id: String = "",
    val label: String = "",
    val path: String = "",
    val type: String = "sendreceive",
    val filesystemType: String = "basic",
    val devices: List<FolderDevice> = emptyList(),
    val rescanIntervalS: Int = 3600,
    val fsWatcherEnabled: Boolean = true,
    val fsWatcherDelayS: Double = 10.0,
    val ignorePerms: Boolean = true,
    val autoNormalize: Boolean = true,
    val paused: Boolean = false,
    val ignoreDelete: Boolean = false,
    val maxConflicts: Int = 10,
)

@Serializable
data class FolderDevice(
    val deviceID: String = "",
    val introducedBy: String = "",
    val encryptionPassword: String = "",
)

@Serializable
data class Device(
    val deviceID: String = "",
    val name: String = "",
    val addresses: List<String> = listOf("dynamic"),
    val compression: String = "metadata",
    val introducer: Boolean = false,
    val paused: Boolean = false,
    val autoAcceptFolders: Boolean = false,
    val maxSendKbps: Int = 0,
    val maxRecvKbps: Int = 0,
    val untrusted: Boolean = false,
    val numConnections: Int = 0,
)

@Serializable
data class FolderStatus(
    val globalBytes: Long = 0,
    val globalFiles: Int = 0,
    val globalDirectories: Int = 0,
    val globalDeleted: Int = 0,
    val globalTotalItems: Int = 0,
    val inSyncBytes: Long = 0,
    val inSyncFiles: Int = 0,
    val localBytes: Long = 0,
    val localFiles: Int = 0,
    val localDirectories: Int = 0,
    val localDeleted: Int = 0,
    val localTotalItems: Int = 0,
    val needBytes: Long = 0,
    val needFiles: Int = 0,
    val needDirectories: Int = 0,
    val needDeletes: Int = 0,
    val needTotalItems: Int = 0,
    val pullErrors: Int = 0,
    val state: String = "",
    val stateChanged: String = "",
    val sequence: Long = 0,
    val version: Int = 0,
)

package dev.lostf1sh.syncthing.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class Versioning(
    val type: String = "",
    val params: Map<String, String> = emptyMap(),
)

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
    val versioning: Versioning? = null,
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

/** Fields editable by the app when patching an existing device configuration. */
@Serializable
data class DeviceUpdate(
    val name: String,
    val addresses: List<String>,
    val compression: String,
    val introducer: Boolean,
    val autoAcceptFolders: Boolean,
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

@Serializable
data class PendingFolder(
    val offeredBy: Map<String, PendingFolderInfo> = emptyMap(),
)

@Serializable
data class PendingFolderInfo(
    val time: String = "",
    val label: String = "",
    val receiveEncrypted: Boolean = false,
    val remoteEncrypted: Boolean = false,
)

@Serializable
data class PendingDevice(
    val time: String = "",
    val name: String = "",
    val address: String = "",
)

@Serializable
data class FolderCompletionInfo(
    val completion: Double = 0.0,
    val globalBytes: Long = 0,
    val globalItems: Int = 0,
    val needBytes: Long = 0,
    val needItems: Int = 0,
    val needDeletes: Int = 0,
    val remoteState: String = "",
    val sequence: Long = 0,
)

@Serializable
data class FolderErrorList(
    val folder: String = "",
    val errors: List<FolderError> = emptyList(),
    val page: Int = 1,
    val perpage: Int = 65536,
)

@Serializable
data class FolderError(
    val error: String = "",
    val path: String = "",
)

@Serializable
data class SystemConfig(
    val folders: List<Folder> = emptyList(),
    val devices: List<Device> = emptyList(),
)

@Serializable
data class SystemLogResponse(
    val messages: List<LogMessage> = emptyList(),
)

@Serializable
data class LogMessage(
    @kotlinx.serialization.SerialName("when")
    val timestamp: String = "",
    val message: String = "",
    val level: Int = 0,
)

// Ported from Catfriend1/syncthing-android (MPL-2.0): service/EventProcessor.java
// Event type taxonomy extracted into Kotlin sealed hierarchy.
package dev.sirulex.syncthing.api.events

/**
 * Sealed hierarchy of Syncthing events.
 * Parsed from GET /rest/events JSON array.
 */
sealed interface SyncthingEvent {
    val id: Long
    val time: String

    // --- Device events ---
    data class DeviceConnected(
        override val id: Long,
        override val time: String,
        val deviceId: String,
        val address: String,
    ) : SyncthingEvent

    data class DeviceDisconnected(
        override val id: Long,
        override val time: String,
        val deviceId: String,
    ) : SyncthingEvent

    data class DevicePaused(
        override val id: Long,
        override val time: String,
        val deviceId: String,
    ) : SyncthingEvent

    data class DeviceResumed(
        override val id: Long,
        override val time: String,
        val deviceId: String,
    ) : SyncthingEvent

    // --- Folder events ---
    data class StateChanged(
        override val id: Long,
        override val time: String,
        val folderId: String,
        val from: String,
        val to: String,
    ) : SyncthingEvent

    data class FolderSummary(
        override val id: Long,
        override val time: String,
        val folderId: String,
    ) : SyncthingEvent

    data class FolderCompletion(
        override val id: Long,
        override val time: String,
        val folderId: String,
        val deviceId: String,
        val completion: Double,
    ) : SyncthingEvent

    data class FolderPaused(
        override val id: Long,
        override val time: String,
        val folderId: String,
    ) : SyncthingEvent

    data class FolderResumed(
        override val id: Long,
        override val time: String,
        val folderId: String,
    ) : SyncthingEvent

    data class FolderErrors(
        override val id: Long,
        override val time: String,
        val folderId: String,
    ) : SyncthingEvent

    // --- Item events ---
    data class ItemFinished(
        override val id: Long,
        override val time: String,
        val folderId: String,
        val item: String,
        val action: String,
        val error: String?,
    ) : SyncthingEvent

    sealed interface ChangeDetected : SyncthingEvent {
        val folderId: String
        val path: String
        val action: String
    }

    data class LocalChangeDetected(
        override val id: Long,
        override val time: String,
        override val folderId: String,
        override val path: String,
        override val action: String,
    ) : ChangeDetected

    data class RemoteChangeDetected(
        override val id: Long,
        override val time: String,
        override val folderId: String,
        override val path: String,
        override val action: String,
    ) : ChangeDetected

    // --- Config events ---
    data class ConfigSaved(
        override val id: Long,
        override val time: String,
    ) : SyncthingEvent

    // --- Pending events ---
    data class PendingDevicesChanged(
        override val id: Long,
        override val time: String,
    ) : SyncthingEvent

    data class PendingFoldersChanged(
        override val id: Long,
        override val time: String,
    ) : SyncthingEvent

    // --- Catch-all ---
    data class Unknown(
        override val id: Long,
        override val time: String,
        val type: String,
    ) : SyncthingEvent
}

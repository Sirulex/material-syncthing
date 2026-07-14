package dev.lostf1sh.syncthing.data

import dev.lostf1sh.syncthing.api.events.EventStream
import dev.lostf1sh.syncthing.api.events.SyncthingEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Central event hub. Collects events from EventStream and
 * exposes filtered flows for specific screens.
 */
class EventRepository(
    private val eventStream: EventStream,
    private val diskEventStream: EventStream? = null,
) {

    private val _events = MutableSharedFlow<SyncthingEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<SyncthingEvent> = _events.asSharedFlow()

    private val startLock = Any()

    @Volatile
    private var collectJob: Job? = null

    fun start(scope: CoroutineScope) {
        synchronized(startLock) {
            if (collectJob?.isActive == true) return
            collectJob = scope.launch {
                kotlinx.coroutines.coroutineScope {
                    launch { eventStream.events().collect { _events.emit(it) } }
                    diskEventStream?.let { stream ->
                        launch { stream.events().collect { _events.emit(it) } }
                    }
                }
            }
        }
    }

    fun stop() {
        synchronized(startLock) {
            collectJob?.cancel()
            collectJob = null
        }
    }

    /** Folder state changes for a specific folder. */
    fun folderState(folderId: String): Flow<String> = events
        .filter { it is SyncthingEvent.StateChanged && it.folderId == folderId }
        .map { (it as SyncthingEvent.StateChanged).to }

    /** All folder state changes (folderId → state). */
    fun allFolderStates(): Flow<Pair<String, String>> = events
        .filter { it is SyncthingEvent.StateChanged }
        .map { val e = it as SyncthingEvent.StateChanged; e.folderId to e.to }

    /** Device connection changes (deviceId → connected). */
    fun deviceConnections(): Flow<Pair<String, Boolean>> = events
        .filter { it is SyncthingEvent.DeviceConnected || it is SyncthingEvent.DeviceDisconnected }
        .map { event ->
            when (event) {
                is SyncthingEvent.DeviceConnected -> event.deviceId to true
                is SyncthingEvent.DeviceDisconnected -> event.deviceId to false
                else -> "" to false
            }
        }

    /** Folder completion events. */
    fun folderCompletions(): Flow<SyncthingEvent.FolderCompletion> = events
        .filter { it is SyncthingEvent.FolderCompletion }
        .map { it as SyncthingEvent.FolderCompletion }

    /** Item finished events for a folder. */
    fun itemsFinished(folderId: String): Flow<SyncthingEvent.ItemFinished> = events
        .filter { it is SyncthingEvent.ItemFinished && it.folderId == folderId }
        .map { it as SyncthingEvent.ItemFinished }

    /** Config saved events (trigger config refresh). */
    fun configChanges(): Flow<SyncthingEvent.ConfigSaved> = events
        .filter { it is SyncthingEvent.ConfigSaved }
        .map { it as SyncthingEvent.ConfigSaved }

    /** Local and remotely applied file-system changes (recent changes feed). */
    fun recentChanges(): Flow<SyncthingEvent.ChangeDetected> = events
        .filter { it is SyncthingEvent.ChangeDetected }
        .map { it as SyncthingEvent.ChangeDetected }

    /** Pending devices changed events. */
    fun pendingDevicesChanged(): Flow<SyncthingEvent.PendingDevicesChanged> = events
        .filter { it is SyncthingEvent.PendingDevicesChanged }
        .map { it as SyncthingEvent.PendingDevicesChanged }
}

// Ported from Catfriend1/syncthing-android (MPL-2.0): service/EventProcessor.java
// Rewritten as Kotlin Flow with long-polling, replaces Runnable + Handler pattern.
package dev.sirulex.syncthing.api.events

import android.util.Log
import dev.sirulex.syncthing.api.SyncthingClient
import dev.sirulex.syncthing.api.dto.Event
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.ConnectException

/**
 * Long-polls Syncthing /rest/events and emits parsed [SyncthingEvent]s.
 *
 * The server holds the request open for up to [serverTimeoutSec] seconds,
 * so each iteration is effectively a blocking wait for new events.
 */
class EventStream(
    private val client: SyncthingClient,
    private val source: Source = Source.DEFAULT,
) {

    enum class Source { DEFAULT, DISK }

    companion object {
        private const val TAG = "EventStream"
        private const val BACKOFF_MS = 2_000L
        private const val SERVER_TIMEOUT_SEC = 60
    }

    fun events(): Flow<SyncthingEvent> = flow {
        var since = 0L
        while (currentCoroutineContext().isActive) {
            try {
                val batch = when (source) {
                    Source.DEFAULT -> client.events(since = since, timeout = SERVER_TIMEOUT_SEC)
                    Source.DISK -> client.diskEvents(since = since, timeout = SERVER_TIMEOUT_SEC)
                }
                for (raw in batch) {
                    val event = try {
                        parse(raw)
                    } catch (e: Exception) {
                        // Malformed individual event: log, advance `since`, and skip.
                        // Keeps the stream alive when Syncthing adds/changes event shapes.
                        Log.w(TAG, "Skipping unparseable event id=${raw.id} type=${raw.type}", e)
                        SyncthingEvent.Unknown(raw.id, raw.time, raw.type)
                    }
                    emit(event)
                    if (raw.id > since) since = raw.id
                }
            } catch (e: SerializationException) {
                // Whole-batch decode failure. Log and back off; don't let one schema
                // drift kill the stream forever.
                currentCoroutineContext().ensureActive()
                Log.w(TAG, "Event batch deserialization failed; backing off", e)
                delay(BACKOFF_MS)
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                if (!e.isTransientConnectionFailure()) {
                    Log.d(TAG, "Event poll error: ${e.message}")
                }
                delay(BACKOFF_MS)
            }
        }
    }

    private fun Throwable.isTransientConnectionFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ConnectException) return true
            current = current.cause
        }
        return message?.contains("Failed to connect", ignoreCase = true) == true
    }

    private fun parse(raw: Event): SyncthingEvent {
        val data = raw.data
        return when (raw.type) {
            "DeviceConnected" -> SyncthingEvent.DeviceConnected(
                id = raw.id,
                time = raw.time,
                deviceId = data?.get("id")?.jsonPrimitive?.content ?: "",
                address = data?.get("addr")?.jsonPrimitive?.content ?: "",
            )
            "DeviceDisconnected" -> SyncthingEvent.DeviceDisconnected(
                id = raw.id,
                time = raw.time,
                deviceId = data?.get("id")?.jsonPrimitive?.content ?: "",
            )
            "DevicePaused" -> SyncthingEvent.DevicePaused(
                id = raw.id,
                time = raw.time,
                deviceId = data?.get("device")?.jsonPrimitive?.content ?: "",
            )
            "DeviceResumed" -> SyncthingEvent.DeviceResumed(
                id = raw.id,
                time = raw.time,
                deviceId = data?.get("device")?.jsonPrimitive?.content ?: "",
            )
            "StateChanged" -> SyncthingEvent.StateChanged(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("folder")?.jsonPrimitive?.content ?: "",
                from = data?.get("from")?.jsonPrimitive?.content ?: "",
                to = data?.get("to")?.jsonPrimitive?.content ?: "",
            )
            "FolderSummary" -> SyncthingEvent.FolderSummary(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("folder")?.jsonPrimitive?.content ?: "",
            )
            "FolderCompletion" -> SyncthingEvent.FolderCompletion(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("folder")?.jsonPrimitive?.content ?: "",
                deviceId = data?.get("device")?.jsonPrimitive?.content ?: "",
                completion = data?.get("completion")?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
            )
            "FolderPaused" -> SyncthingEvent.FolderPaused(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("id")?.jsonPrimitive?.content ?: "",
            )
            "FolderResumed" -> SyncthingEvent.FolderResumed(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("id")?.jsonPrimitive?.content ?: "",
            )
            "FolderErrors" -> SyncthingEvent.FolderErrors(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("folder")?.jsonPrimitive?.content ?: "",
            )
            "ItemFinished" -> SyncthingEvent.ItemFinished(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("folder")?.jsonPrimitive?.content ?: "",
                item = data?.get("item")?.jsonPrimitive?.content ?: "",
                action = data?.get("action")?.jsonPrimitive?.content ?: "",
                error = data?.get("error")?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotBlank() },
            )
            "LocalChangeDetected" -> SyncthingEvent.LocalChangeDetected(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("folder")?.jsonPrimitive?.content
                    ?: data?.get("folderID")?.jsonPrimitive?.content
                    ?: "",
                path = data?.get("path")?.jsonPrimitive?.content ?: "",
                action = data?.get("action")?.jsonPrimitive?.content ?: "",
            )
            "RemoteChangeDetected" -> SyncthingEvent.RemoteChangeDetected(
                id = raw.id,
                time = raw.time,
                folderId = data?.get("folder")?.jsonPrimitive?.content
                    ?: data?.get("folderID")?.jsonPrimitive?.content
                    ?: "",
                path = data?.get("path")?.jsonPrimitive?.content ?: "",
                action = data?.get("action")?.jsonPrimitive?.content ?: "",
            )
            "ConfigSaved" -> SyncthingEvent.ConfigSaved(
                id = raw.id,
                time = raw.time,
            )
            "PendingDevicesChanged" -> SyncthingEvent.PendingDevicesChanged(
                id = raw.id,
                time = raw.time,
            )
            "PendingFoldersChanged" -> SyncthingEvent.PendingFoldersChanged(
                id = raw.id,
                time = raw.time,
            )
            else -> SyncthingEvent.Unknown(
                id = raw.id,
                time = raw.time,
                type = raw.type,
            )
        }
    }
}

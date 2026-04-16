package dev.lostf1sh.syncthing.data

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.lostf1sh.syncthing.api.events.SyncthingEvent

/**
 * Smart notification policy: only surfaces meaningful events,
 * with throttling to avoid notification spam.
 */
class NotificationPolicy(private val context: Context) {

    companion object {
        private const val TAG = "NotificationPolicy"
        private const val CHANNEL_EVENTS = "syncthing_events"
        private const val THROTTLE_MS = 30_000L
        private const val ID_SYNC_COMPLETE = 100
        private const val ID_DEVICE_CONNECTED = 101
        private const val ID_FOLDER_ERROR = 102
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastNotified = mutableMapOf<String, Long>()

    var notifySyncComplete: Boolean = true
    var notifyDeviceConnected: Boolean = false
    var notifyErrors: Boolean = true

    fun onEvent(event: SyncthingEvent) {
        when (event) {
            is SyncthingEvent.StateChanged -> {
                if (notifySyncComplete && event.from == "syncing" && event.to == "idle") {
                    throttledNotify("sync-${event.folderId}", ID_SYNC_COMPLETE) {
                        NotificationCompat.Builder(context, CHANNEL_EVENTS)
                            .setContentTitle("Sync Complete")
                            .setContentText("Folder ${event.folderId} is up to date")
                            .setSmallIcon(android.R.drawable.ic_popup_sync)
                            .setAutoCancel(true)
                            .build()
                    }
                }
            }
            is SyncthingEvent.DeviceConnected -> {
                if (notifyDeviceConnected) {
                    throttledNotify("device-${event.deviceId}", ID_DEVICE_CONNECTED) {
                        NotificationCompat.Builder(context, CHANNEL_EVENTS)
                            .setContentTitle("Device Connected")
                            .setContentText(event.deviceId.take(7))
                            .setSmallIcon(android.R.drawable.ic_popup_sync)
                            .setAutoCancel(true)
                            .build()
                    }
                }
            }
            is SyncthingEvent.FolderErrors -> {
                if (notifyErrors) {
                    throttledNotify("errors-${event.folderId}", ID_FOLDER_ERROR) {
                        NotificationCompat.Builder(context, CHANNEL_EVENTS)
                            .setContentTitle("Sync Errors")
                            .setContentText("Errors in folder ${event.folderId}")
                            .setSmallIcon(android.R.drawable.ic_dialog_alert)
                            .setAutoCancel(true)
                            .build()
                    }
                }
            }
            else -> { }
        }
    }

    private inline fun throttledNotify(
        key: String,
        id: Int,
        builder: () -> android.app.Notification,
    ) {
        val now = System.currentTimeMillis()
        val last = lastNotified[key] ?: 0
        if (now - last < THROTTLE_MS) return
        lastNotified[key] = now
        try {
            nm.notify(id, builder())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post notification", e)
        }
    }
}

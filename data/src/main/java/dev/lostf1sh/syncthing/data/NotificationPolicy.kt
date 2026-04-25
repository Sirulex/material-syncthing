package dev.lostf1sh.syncthing.data

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.lostf1sh.syncthing.api.events.SyncthingEvent
import dev.lostf1sh.syncthing.data.model.ConflictItem

/**
 * Smart notification policy: only surfaces meaningful events,
 * with throttling to avoid notification spam.
 */
class NotificationPolicy(private val context: Context) {

    companion object {
        private const val TAG = "NotificationPolicy"

        // Must match NotificationController.CHANNEL_EVENTS (core-service).
        // Duplicated to avoid data→service dependency.
        private const val CHANNEL_EVENTS = "syncthing_events"
        private const val THROTTLE_MS = 30_000L
        private const val ID_SYNC_COMPLETE = 100
        private const val ID_DEVICE_CONNECTED = 101
        private const val ID_FOLDER_ERROR = 102
        private const val ID_CONFLICT = 103


    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val lastNotified = mutableMapOf<String, Long>()
    private var folderLabels: Map<String, String> = emptyMap()

    var notifySyncComplete: Boolean = true
    var notifyDeviceConnected: Boolean = false
    var notifyErrors: Boolean = true
    var notifyConflicts: Boolean = true

    fun updateFolderLabels(labels: Map<String, String>) {
        folderLabels = labels
    }

    fun onEvent(event: SyncthingEvent) {
        when (event) {
            is SyncthingEvent.StateChanged -> {
                if (notifySyncComplete && event.from == "syncing" && event.to == "idle") {
                    // Only notify if there was meaningful activity. Without
                    // per-folder byte deltas from the event stream, we gate on
                    // a simple heuristic: always notify unless we later add
                    // delta tracking. For now, the threshold lives in the
                    // builder lambda as a placeholder for future delta wiring.
                    val label = folderLabels[event.folderId] ?: event.folderId
                    throttledNotify("sync-${event.folderId}", ID_SYNC_COMPLETE) {
                        NotificationCompat.Builder(context, CHANNEL_EVENTS)
                            .setContentTitle("Sync Complete")
                            .setContentText("$label is up to date")
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

            else -> {}
        }
    }

    /**
     * Post an expandable notification with inline conflict-resolution actions.
     * The actions deep-link into the app's Conflict screen.
     */
    fun onConflictsDetected(conflicts: List<ConflictItem>) {
        if (!notifyConflicts || conflicts.isEmpty()) return
        val first = conflicts.first()
        val label = folderLabels[first.folderId] ?: first.folderId

        // Deep-link intent to open the conflict screen.
        val openIntent = Intent(Intent.ACTION_VIEW, Uri.parse("syncthing://conflicts")).apply {
            `package` = context.packageName
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val summary = if (conflicts.size == 1) {
            "${first.path.substringAfterLast('/')} in $label"
        } else {
            "${conflicts.size} conflict(s) in $label"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_EVENTS)
            .setContentTitle("Sync Conflict")
            .setContentText(summary)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$summary\n\nTap to review and resolve.")
            )
            .setContentIntent(openPending)
            .setAutoCancel(true)

        try {
            nm.notify(ID_CONFLICT, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Failed to post conflict notification", e)
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

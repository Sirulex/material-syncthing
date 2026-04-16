// Ported from Catfriend1/syncthing-android (MPL-2.0): service/NotificationHandler.java
// Simplified: removed consent notifications (handled by EventProcessor in Phase 7),
// removed pre-Android-8 paths (minSdk 28).
package dev.lostf1sh.syncthing.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.lostf1sh.syncthing.native.RunState

class NotificationController(private val context: Context) {

    companion object {
        const val ID_PERSISTENT = 1
        private const val ID_CRASH = 9
        private const val CHANNEL_PERSISTENT = "syncthing_persistent"
        private const val CHANNEL_INFO = "syncthing_info"
        const val CHANNEL_EVENTS = "syncthing_events"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createChannels()
    }

    private fun createChannels() {
        val persistent = NotificationChannel(
            CHANNEL_PERSISTENT,
            "Syncthing Service",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
            setShowBadge(false)
            lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
        }

        val info = NotificationChannel(
            CHANNEL_INFO,
            "Syncthing Notifications",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            enableVibration(false)
            setSound(null, null)
            setShowBadge(true)
        }

        val events = NotificationChannel(
            CHANNEL_EVENTS,
            "Syncthing Events",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            enableVibration(false)
            setSound(null, null)
            setShowBadge(true)
        }

        notificationManager.createNotificationChannels(listOf(persistent, info, events))
    }

    fun buildPersistentNotification(state: RunState): Notification {
        val text = when (state) {
            is RunState.Stopped -> "Syncthing stopped"
            is RunState.Starting -> "Syncthing starting..."
            is RunState.Running -> "Syncthing running"
            is RunState.Crashed -> "Syncthing crashed (${state.exitCode})"
            is RunState.Paused -> "Paused: ${state.reason}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setContentTitle("Syncthing")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)

        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        if (openIntent != null) {
            val pendingOpen = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.setContentIntent(pendingOpen)
        }

        addStateActions(builder, state)

        return builder.build()
    }

    private fun addStateActions(builder: NotificationCompat.Builder, state: RunState) {
        when (state) {
            is RunState.Running -> {
                builder.addAction(
                    android.R.drawable.ic_popup_sync,
                    "Rescan all",
                    serviceAction(SyncthingService.ACTION_RESCAN_ALL, 1),
                )
                builder.addAction(
                    android.R.drawable.ic_media_pause,
                    "Pause",
                    serviceAction(SyncthingService.ACTION_PAUSE, 2),
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    serviceAction(SyncthingService.ACTION_STOP, 3),
                )
            }
            is RunState.Paused -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Resume",
                    serviceAction(SyncthingService.ACTION_START, 4),
                )
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    serviceAction(SyncthingService.ACTION_STOP, 5),
                )
            }
            is RunState.Crashed,
            is RunState.Stopped -> {
                builder.addAction(
                    android.R.drawable.ic_media_play,
                    "Start",
                    serviceAction(SyncthingService.ACTION_START, 6),
                )
            }
            is RunState.Starting -> {
                builder.addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    "Stop",
                    serviceAction(SyncthingService.ACTION_STOP, 7),
                )
            }
        }
    }

    private fun serviceAction(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, SyncthingService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun showCrashedNotification(exitCode: Int, reason: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_INFO)
            .setContentTitle("Syncthing crashed")
            .setContentText("Exit code $exitCode: $reason")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(ID_CRASH, notification)
    }

    fun cancelAll() {
        notificationManager.cancelAll()
    }
}

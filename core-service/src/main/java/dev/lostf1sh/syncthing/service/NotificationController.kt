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

        notificationManager.createNotificationChannels(listOf(persistent, info))
    }

    fun buildPersistentNotification(state: RunState): Notification {
        val text = when (state) {
            is RunState.Stopped -> "Syncthing stopped"
            is RunState.Starting -> "Syncthing starting..."
            is RunState.Running -> "Syncthing running"
            is RunState.Crashed -> "Syncthing crashed (${state.exitCode})"
            is RunState.Paused -> "Paused: ${state.reason}"
        }

        val openIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
        val pendingOpen = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(context, CHANNEL_PERSISTENT)
            .setContentTitle("Syncthing")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(pendingOpen)
            .build()
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

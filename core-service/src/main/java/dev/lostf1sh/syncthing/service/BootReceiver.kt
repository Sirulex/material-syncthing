// Ported from Catfriend1/syncthing-android (MPL-2.0): receiver/BootReceiver.java
package dev.lostf1sh.syncthing.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Starts SyncthingService on boot or package update, if enabled in settings.
 *
 * Test with: adb shell am broadcast -a android.intent.action.BOOT_COMPLETED
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val validAction = intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        if (!validAction) return

        // TODO: Check start-on-boot preference from DataStore (Phase 8)
        // For now, always start on boot/update
        Log.i(TAG, "Boot/update received, starting service")
        val serviceIntent = Intent(context, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_START
        }
        try {
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // Android 14+ may throw ForegroundServiceStartNotAllowedException when
            // starting a data-sync FGS from a background broadcast. Log and move on;
            // the user can launch the app manually.
            Log.w(TAG, "Could not start foreground service from boot", e)
        }
    }
}

package dev.lostf1sh.syncthing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import dev.lostf1sh.syncthing.service.SyncthingService
import dev.lostf1sh.syncthing.ui.core.theme.SyncthingTheme
import dev.lostf1sh.syncthing.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Start syncthing service on app launch
        startSyncthingService()

        setContent {
            SyncthingTheme {
                AppNavigation()
            }
        }
    }

    private fun startSyncthingService() {
        val intent = Intent(this, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }
}

package dev.lostf1sh.syncthing

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import dev.lostf1sh.syncthing.service.SyncthingService
import dev.lostf1sh.syncthing.ui.core.theme.SyncthingTheme
import dev.lostf1sh.syncthing.ui.navigation.AppNavigation

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestStoragePermission()
        startSyncthingService()

        setContent {
            SyncthingTheme {
                // Root Surface prevents window-bg flicker during nav / predictive back.
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }

    private fun startSyncthingService() {
        val intent = Intent(this, SyncthingService::class.java).apply {
            action = SyncthingService.ACTION_START
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName"),
                )
                startActivity(intent)
            }
        }
    }
}

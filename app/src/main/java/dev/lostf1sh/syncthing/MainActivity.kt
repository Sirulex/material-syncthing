package dev.lostf1sh.syncthing

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import dev.lostf1sh.syncthing.service.SyncthingService
import dev.lostf1sh.syncthing.ui.core.theme.SyncthingTheme
import dev.lostf1sh.syncthing.ui.navigation.AppNavigation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Don't launch the settings activity or start the foreground service
        // until the user has finished onboarding. Previously this fired before
        // any permission had been granted and before the user knew the app
        // was going to run a background service.
        maybeStartServiceAfterOnboarding()

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

    private fun maybeStartServiceAfterOnboarding() {
        val app = applicationContext as SyncthingApp
        lifecycleScope.launch {
            val done = app.container.settingsStore.onboardingComplete.first()
            if (done) {
                val intent = Intent(this@MainActivity, SyncthingService::class.java).apply {
                    action = SyncthingService.ACTION_START
                }
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }
    }
}

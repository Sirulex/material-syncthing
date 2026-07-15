package dev.sirulex.syncthing

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import dev.sirulex.syncthing.service.SyncthingService
import dev.sirulex.syncthing.ui.core.theme.SyncthingTheme
import dev.sirulex.syncthing.ui.navigation.AppNavigation
import dev.sirulex.syncthing.ui.navigation.PendingShortcut
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val pendingShortcut = mutableStateOf<PendingShortcut?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val consumed = handleIntent(intent)
        // Don't launch the settings activity or start the foreground service
        // until the user has finished onboarding. Previously this fired before
        // any permission had been granted and before the user knew the app
        // was going to run a background service. Also skip the ACTION_START
        // here when a service-control shortcut just dispatched PAUSE/STOP —
        // otherwise the two actions race and the shortcut's effect is lost.
        if (!consumed) {
            maybeStartServiceAfterOnboarding()
        }

        setContent {
            val app = applicationContext as SyncthingApp
            val theme by app.container.settingsStore.theme.collectAsStateWithLifecycle(initialValue = "system")
            val darkTheme = when (theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            SyncthingTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation(pendingShortcut = pendingShortcut)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /**
     * Returns true when the intent is a service-control shortcut that finishes
     * the activity and should bypass the default ACTION_START path.
     */
    private fun handleIntent(intent: Intent?): Boolean {
        when (intent?.action) {
            ACTION_SHORTCUT_START -> {
                val serviceIntent = Intent(this, SyncthingService::class.java).apply {
                    action = SyncthingService.ACTION_START
                }
                ContextCompat.startForegroundService(this, serviceIntent)
                finish()
                return true
            }
            ACTION_SHORTCUT_RESCAN_ALL -> {
                dispatchServiceControlShortcut(SyncthingService.ACTION_RESCAN_ALL)
                finish()
                return true
            }
            ACTION_SHORTCUT_PAUSE -> {
                dispatchServiceControlShortcut(SyncthingService.ACTION_PAUSE)
                finish()
                return true
            }
            ACTION_SHORTCUT_ERROR_CENTER -> {
                pendingShortcut.value = PendingShortcut.ErrorCenter
            }
            ACTION_SHORTCUT_INSIGHTS -> {
                pendingShortcut.value = PendingShortcut.Insights
            }
            Intent.ACTION_SEND -> {
                val uri = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                }
                if (uri != null) pendingShortcut.value = PendingShortcut.Share(listOf(uri))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris: List<android.net.Uri> = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                        ?: emptyList()
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra<android.net.Uri>(Intent.EXTRA_STREAM)
                        ?: emptyList()
                }
                if (uris.isNotEmpty()) pendingShortcut.value = PendingShortcut.Share(uris)
            }
        }
        return false
    }

    /**
     * Dispatch RESCAN_ALL / PAUSE shortcut actions.
     *
     * Use [Context.startService] (NOT startForegroundService) because PAUSE and
     * RESCAN_ALL don't promote the service to foreground — they route to
     * pauseSyncthing() / rescanAllFolders(), neither of which calls
     * startForeground(). Promoting to FGS would trigger
     * ForegroundServiceDidNotStartInTimeException on API 31+ when the service
     * is cold-starting. For a cold start, the service boots in background and
     * these actions become no-ops (service not Running) — acceptable: PAUSE on
     * a non-running service is already what the user wants.
     */
    private fun dispatchServiceControlShortcut(action: String) {
        val intent = Intent(this, SyncthingService::class.java).apply { this.action = action }
        try {
            startService(intent)
        } catch (_: Exception) {
            // Background-service launch restriction — ignore; foregrounding the
            // service just to forward a pause/rescan isn't worth the FGS crash.
        }
    }

    private fun maybeStartServiceAfterOnboarding() {
        val app = applicationContext as SyncthingApp
        lifecycleScope.launch {
            val done = app.container.settingsStore.onboardingComplete.first()
            val startSuppressedByUser = app.container.settingsStore.startSuppressedByUser.first()
            if (shouldAutoStartAfterOnboarding(done, startSuppressedByUser)) {
                val intent = Intent(this@MainActivity, SyncthingService::class.java).apply {
                    action = SyncthingService.ACTION_START
                }
                ContextCompat.startForegroundService(this@MainActivity, intent)
            }
        }
    }

    companion object {
        const val ACTION_SHORTCUT_START = "dev.sirulex.syncthing.shortcut.START"
        const val ACTION_SHORTCUT_RESCAN_ALL = "dev.sirulex.syncthing.shortcut.RESCAN_ALL"
        const val ACTION_SHORTCUT_PAUSE = "dev.sirulex.syncthing.shortcut.PAUSE"
        const val ACTION_SHORTCUT_ERROR_CENTER = "dev.sirulex.syncthing.shortcut.ERROR_CENTER"
        const val ACTION_SHORTCUT_INSIGHTS = "dev.sirulex.syncthing.shortcut.INSIGHTS"
    }
}

internal fun shouldAutoStartAfterOnboarding(
    onboardingComplete: Boolean,
    startSuppressedByUser: Boolean,
): Boolean = onboardingComplete && !startSuppressedByUser

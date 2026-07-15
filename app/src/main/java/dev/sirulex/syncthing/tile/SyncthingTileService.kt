package dev.sirulex.syncthing.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.sirulex.syncthing.R
import dev.sirulex.syncthing.native.RunState
import dev.sirulex.syncthing.service.SyncthingService
import dev.sirulex.syncthing.ui.core.displayLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.N)
class SyncthingTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collectJob: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        applyState(SyncthingService.state.value)
        collectJob = scope.launch {
            SyncthingService.state.collect { applyState(it) }
        }
    }

    override fun onStopListening() {
        collectJob?.cancel()
        collectJob = null
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onClick() {
        super.onClick()
        val state = SyncthingService.state.value
        val action = when (state) {
            is RunState.Running, is RunState.Starting -> SyncthingService.ACTION_PAUSE
            else -> SyncthingService.ACTION_START
        }
        val intent = Intent(applicationContext, SyncthingService::class.java).apply {
            this.action = action
        }
        // PAUSE on a cold service must not use startForegroundService — the
        // service won't call startForeground() and will crash with
        // ForegroundServiceDidNotStartInTimeException on API 31+.
        if (action == SyncthingService.ACTION_START) {
            applicationContext.startForegroundService(intent)
        } else {
            applicationContext.startService(intent)
        }
    }

    private fun applyState(state: RunState) {
        val tile = qsTile ?: return
        tile.label = "Syncthing"
        tile.icon = Icon.createWithResource(applicationContext, R.drawable.ic_launcher_monochrome)
        tile.state = when (state) {
            is RunState.Running -> Tile.STATE_ACTIVE
            is RunState.Starting -> Tile.STATE_ACTIVE
            is RunState.Stopped, is RunState.Paused, is RunState.Crashed -> Tile.STATE_INACTIVE
        }
        tile.subtitle = state.displayLabel()
        tile.updateTile()
    }
}

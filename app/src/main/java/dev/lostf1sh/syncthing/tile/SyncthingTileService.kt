package dev.lostf1sh.syncthing.tile

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import dev.lostf1sh.syncthing.R
import dev.lostf1sh.syncthing.native.RunState
import dev.lostf1sh.syncthing.service.SyncthingService
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
        val intent = Intent(applicationContext, SyncthingService::class.java).apply {
            action = when (state) {
                is RunState.Running, is RunState.Starting -> SyncthingService.ACTION_PAUSE
                else -> SyncthingService.ACTION_START
            }
        }
        applicationContext.startForegroundService(intent)
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
        tile.subtitle = when (state) {
            is RunState.Stopped -> "Stopped"
            is RunState.Starting -> "Starting…"
            is RunState.Running -> "Running"
            is RunState.Paused -> "Paused"
            is RunState.Crashed -> "Crashed"
        }
        tile.updateTile()
    }
}

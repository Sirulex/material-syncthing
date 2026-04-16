// Ported from Catfriend1/syncthing-android (MPL-2.0): service/SyncthingService.java
// Rewritten with coroutines, StateFlow, and modern FGS API.
package dev.lostf1sh.syncthing.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dev.lostf1sh.syncthing.native.ConfigBootstrapper
import dev.lostf1sh.syncthing.native.NativeLauncher
import dev.lostf1sh.syncthing.native.RunState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class SyncthingService : Service() {

    companion object {
        private const val TAG = "SyncthingService"
        const val ACTION_START = "dev.lostf1sh.syncthing.action.START"
        const val ACTION_STOP = "dev.lostf1sh.syncthing.action.STOP"
        const val ACTION_PAUSE = "dev.lostf1sh.syncthing.action.PAUSE"

        private val _state = MutableStateFlow<RunState>(RunState.Stopped)
        val state: StateFlow<RunState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncthingJob: Job? = null
    private lateinit var launcher: NativeLauncher
    private lateinit var bootstrapper: ConfigBootstrapper
    private lateinit var notifications: NotificationController

    override fun onCreate() {
        super.onCreate()
        launcher = NativeLauncher.fromContext(this)
        bootstrapper = ConfigBootstrapper(filesDir)
        notifications = NotificationController(this)
        Log.i(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSyncthing()
            ACTION_PAUSE -> pauseSyncthing("User requested pause")
            else -> startSyncthing()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "Service destroying")
        syncthingJob?.cancel()
        syncthingJob = null
        runBlocking {
            if (launcher.isRunning) {
                launcher.stop()
            }
        }
        _state.value = RunState.Stopped
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startSyncthing() {
        if (syncthingJob?.isActive == true) {
            Log.w(TAG, "Syncthing already running, ignoring start")
            return
        }

        _state.value = RunState.Starting
        startForegroundWithNotification(RunState.Starting)

        syncthingJob = serviceScope.launch(Dispatchers.IO) {
            try {
                // Bootstrap config on first run
                if (!bootstrapper.configExists) {
                    Log.i(TAG, "First run — generating config")
                    launcher.generateConfig()
                    val deviceId = launcher.getDeviceId()
                    bootstrapper.patchConfig(deviceId)
                }

                val apiKey = bootstrapper.readApiKey()
                val port = bootstrapper.readGuiPort()
                _state.value = RunState.Running(apiKey, port)
                updateNotification(RunState.Running(apiKey, port))

                // Blocks until process exits
                val exitCode = launcher.start()
                val newState = launcher.interpretExitCode(exitCode)
                _state.value = newState

                when (newState) {
                    is RunState.Crashed -> {
                        notifications.showCrashedNotification(exitCode, newState.reason)
                        stopSelf()
                    }
                    is RunState.Starting -> {
                        // Exit code 3 = restart requested
                        Log.i(TAG, "Restart requested by Syncthing")
                        syncthingJob = null
                        startSyncthing()
                    }
                    else -> stopSelf()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Syncthing", e)
                _state.value = RunState.Crashed(-1, e.message ?: "Unknown error")
                notifications.showCrashedNotification(-1, e.message ?: "Unknown error")
                stopSelf()
            }
        }
    }

    private fun stopSyncthing() {
        serviceScope.launch {
            if (launcher.isRunning) {
                launcher.stop()
            }
            _state.value = RunState.Stopped
            syncthingJob?.cancel()
            syncthingJob = null
            stopSelf()
        }
    }

    private fun pauseSyncthing(reason: String) {
        serviceScope.launch {
            if (launcher.isRunning) {
                launcher.stop()
            }
            syncthingJob?.cancel()
            syncthingJob = null
            _state.value = RunState.Paused(reason)
            updateNotification(RunState.Paused(reason))
        }
    }

    private fun startForegroundWithNotification(state: RunState) {
        val notification = notifications.buildPersistentNotification(state)
        ServiceCompat.startForeground(
            this,
            NotificationController.ID_PERSISTENT,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            else 0,
        )
    }

    private fun updateNotification(state: RunState) {
        val notification = notifications.buildPersistentNotification(state)
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.notify(NotificationController.ID_PERSISTENT, notification)
    }
}

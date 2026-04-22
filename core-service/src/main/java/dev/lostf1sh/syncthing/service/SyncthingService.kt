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
import dev.lostf1sh.syncthing.data.SettingsStore
import dev.lostf1sh.syncthing.data.SyncConstraints
import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.native.ConfigBootstrapper
import dev.lostf1sh.syncthing.native.NativeLauncher
import dev.lostf1sh.syncthing.native.RunState
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class SyncthingService : Service() {

    companion object {
        private const val TAG = "SyncthingService"
        private const val API_READY_TIMEOUT_MS = 60_000L
        const val ACTION_START = "dev.lostf1sh.syncthing.action.START"
        const val ACTION_STOP = "dev.lostf1sh.syncthing.action.STOP"
        const val ACTION_PAUSE = "dev.lostf1sh.syncthing.action.PAUSE"
        const val ACTION_RESCAN_ALL = "dev.lostf1sh.syncthing.action.RESCAN_ALL"

        private val _state = MutableStateFlow<RunState>(RunState.Stopped)
        val state: StateFlow<RunState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncthingJob: Job? = null
    private var constraintJob: Job? = null
    @Volatile private var pausedByConstraint = false
    @Volatile private var requestedPauseReason: String? = null
    private lateinit var launcher: NativeLauncher
    private lateinit var bootstrapper: ConfigBootstrapper
    private lateinit var notifications: NotificationController
    private lateinit var settings: SettingsStore
    private lateinit var constraints: SyncConstraints

    override fun onCreate() {
        super.onCreate()
        launcher = NativeLauncher.fromContext(this)
        bootstrapper = ConfigBootstrapper(filesDir)
        notifications = NotificationController(this)
        settings = SettingsStore(applicationContext)
        constraints = SyncConstraints(applicationContext)
        observeConstraints()
        Log.i(TAG, "Service created")
    }

    private fun observeConstraints() {
        constraintJob?.cancel()
        constraintJob = serviceScope.launch {
            constraints.observe(settings).collect { state ->
                when (state) {
                    is SyncConstraints.ConstraintState.ShouldPause -> {
                        if (launcher.isRunning) {
                            Log.i(TAG, "Constraint pause: ${state.reason}")
                            pausedByConstraint = true
                            pauseSyncthing(state.reason)
                        }
                    }
                    is SyncConstraints.ConstraintState.ShouldRun -> {
                        if (pausedByConstraint && !launcher.isRunning) {
                            Log.i(TAG, "Constraints satisfied; auto-resume")
                            pausedByConstraint = false
                            startSyncthing()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                pausedByConstraint = false
                requestedPauseReason = null
                stopSyncthing()
            }
            ACTION_PAUSE -> {
                pausedByConstraint = false
                pauseSyncthing("User requested pause")
            }
            ACTION_RESCAN_ALL -> rescanAllFolders()
            else -> {
                pausedByConstraint = false
                requestedPauseReason = null
                startSyncthing()
            }
        }
        return START_STICKY
    }

    private fun rescanAllFolders() {
        val running = _state.value as? RunState.Running ?: return
        serviceScope.launch(Dispatchers.IO) {
            try {
                val client = dev.lostf1sh.syncthing.api.SyncthingClient(
                    baseUrl = "http://127.0.0.1:${running.port}",
                    apiKey = running.apiKey,
                )
                try {
                    val list: List<dev.lostf1sh.syncthing.api.dto.Folder> = client.folders()
                    list.forEach { folder ->
                        try { client.rescanFolder(folder.id) } catch (_: Exception) { }
                    }
                } finally {
                    client.close()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rescan-all failed", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "Service destroying")
        constraintJob?.cancel()
        constraintJob = null
        // Kill binary synchronously BEFORE cancelling the coroutine that holds waitFor().
        // Process.waitFor() is not cancellable by coroutine cancel, so the coroutine
        // would otherwise race with an unrelated scope and orphan the native process.
        if (::launcher.isInitialized && launcher.isRunning) {
            try {
                runBlocking {
                    withTimeout(5_000) { launcher.stop() }
                }
            } catch (e: TimeoutCancellationException) {
                Log.w(TAG, "Stop timed out; binary may be orphaned", e)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping binary in onDestroy", e)
            }
        }
        syncthingJob?.cancel()
        syncthingJob = null
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
            // Synchronous constraint check before firing the binary. The
            // observeConstraints flow is upstream-deduped via
            // distinctUntilChanged, so if the first emission said ShouldPause
            // while the launcher was still not-running, no later emission will
            // re-fire. Evaluate current state directly here.
            try {
                val gate = constraints.observe(settings).first()
                if (gate is SyncConstraints.ConstraintState.ShouldPause) {
                    Log.i(TAG, "Constraint gate blocked start: ${gate.reason}")
                    pausedByConstraint = true
                    _state.value = RunState.Paused(gate.reason)
                    updateNotification(RunState.Paused(gate.reason))
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Constraint gate read failed; proceeding", e)
            }
            try {
                // Bootstrap config on first run
                if (!bootstrapper.configExists) {
                    Log.i(TAG, "First run — generating config")
                    launcher.generateConfig()
                }

                // Re-patch every start. Users can carry old/bad config forward,
                // and without this the UI can point at localhost:8384 while the
                // daemon GUI is disabled, TLS-only, or bound elsewhere.
                val deviceId = try {
                    launcher.getDeviceId()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read device ID before start; patching config without local name", e)
                    null
                }
                bootstrapper.patchConfig(deviceId)

                val apiKey = bootstrapper.readApiKey()
                val port = bootstrapper.readGuiPort()

                val processJob = async { launcher.start() }
                if (!waitForApiReady(apiKey, port, processJob)) {
                    val crash = RunState.Crashed(
                        -1,
                        "Syncthing API did not become reachable on 127.0.0.1:$port",
                    )
                    _state.value = crash
                    notifications.showCrashedNotification(crash.exitCode, crash.reason)
                    if (!processJob.isCompleted && launcher.isRunning) {
                        launcher.stop()
                    }
                    if (processJob.isCompleted) {
                        Log.w(TAG, "Syncthing exited before API became ready with code ${processJob.await()}")
                    }
                    stopSelf()
                    return@launch
                }

                val running = RunState.Running(apiKey, port)
                _state.value = running
                updateNotification(running)

                val exitCode = processJob.await()
                val newState = launcher.interpretExitCode(exitCode)
                val pauseReason = requestedPauseReason
                if (pauseReason != null && newState is RunState.Stopped) {
                    requestedPauseReason = null
                    val paused = RunState.Paused(pauseReason)
                    _state.value = paused
                    updateNotification(paused)
                    return@launch
                }
                _state.value = newState

                when (newState) {
                    is RunState.Crashed -> {
                        notifications.showCrashedNotification(exitCode, newState.reason)
                        stopSelf()
                    }
                    is RunState.Starting -> {
                        // Exit code 3 = restart requested. Schedule on Main so the
                        // current job unwinds before startSyncthing() mutates job state.
                        Log.i(TAG, "Restart requested by Syncthing")
                        serviceScope.launch(Dispatchers.Main) {
                            syncthingJob = null
                            startSyncthing()
                        }
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

    private suspend fun waitForApiReady(apiKey: String, port: Int, processJob: Job): Boolean {
        val ready = withTimeoutOrNull(API_READY_TIMEOUT_MS) {
            val client = SyncthingClient(
                baseUrl = "http://127.0.0.1:$port",
                apiKey = apiKey,
            )
            try {
                while (!processJob.isCompleted) {
                    try {
                        if (client.ping().ping == "pong") {
                            Log.i(TAG, "Syncthing API ready on 127.0.0.1:$port")
                            return@withTimeoutOrNull true
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Waiting for Syncthing API on 127.0.0.1:$port: ${e.message}")
                    }
                    delay(500)
                }
                false
            } finally {
                client.close()
            }
        }
        return ready == true
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
            requestedPauseReason = reason
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

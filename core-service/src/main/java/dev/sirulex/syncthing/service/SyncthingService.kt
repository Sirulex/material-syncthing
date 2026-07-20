// Ported from Catfriend1/syncthing-android (MPL-2.0): service/SyncthingService.java
// Rewritten with coroutines, StateFlow, and modern FGS API.
package dev.sirulex.syncthing.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dev.sirulex.syncthing.data.SettingsStore
import dev.sirulex.syncthing.data.SyncConstraints
import dev.sirulex.syncthing.api.SyncthingClient
import dev.sirulex.syncthing.native.ConfigBootstrapper
import dev.sirulex.syncthing.native.NativeLauncher
import dev.sirulex.syncthing.native.RunState
import kotlin.concurrent.thread
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
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
        const val ACTION_START = "dev.sirulex.syncthing.action.START"
        const val ACTION_STOP = "dev.sirulex.syncthing.action.STOP"
        const val ACTION_PAUSE = "dev.sirulex.syncthing.action.PAUSE"
        const val ACTION_RESCAN_ALL = "dev.sirulex.syncthing.action.RESCAN_ALL"

        private val _state = MutableStateFlow<RunState>(RunState.Stopped)
        val state: StateFlow<RunState> = _state.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var syncthingJob: Job? = null
    private var constraintJob: Job? = null
    @Volatile
    private var pauseInProgress = false
    @Volatile
    private var pausedByConstraint = false
    @Volatile
    private var requestedPauseReason: String? = null
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
                        if (pausedByConstraint) {
                            Log.i(TAG, "Constraints satisfied; requesting auto-resume")
                            pausedByConstraint = false
                            // If the constraint pause has already stopped the
                            // process we can resume immediately. Otherwise the
                            // in-flight pause observes pausedByConstraint=false
                            // after launcher.stop() and performs the restart.
                            if (!pauseInProgress && !launcher.isRunning) {
                                requestedPauseReason = null
                                syncthingJob = null
                                startSyncthing()
                            }
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
                stopSyncthing(suppressFutureAutoStarts = true)
            }

            ACTION_PAUSE -> {
                pausedByConstraint = false
                pauseSyncthing(
                    reason = "User requested pause",
                    suppressFutureAutoStarts = true,
                )
            }

            ACTION_RESCAN_ALL -> rescanAllFolders()

            ACTION_START -> {
                pausedByConstraint = false
                requestedPauseReason = null
                serviceScope.launch(Dispatchers.IO) {
                    settings.setStartSuppressedByUser(false)
                }
                startSyncthing()
            }

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
                val client = dev.sirulex.syncthing.api.SyncthingClient(
                    baseUrl = "http://127.0.0.1:${running.port}",
                    apiKey = running.apiKey,
                )
                try {
                    val list: List<dev.sirulex.syncthing.api.dto.Folder> = client.folders()
                    list.forEach { folder ->
                        try {
                            client.rescanFolder(folder.id)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                        }
                    }
                } finally {
                    client.close()
                }
            } catch (e: CancellationException) {
                throw e
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
        // Issue SIGTERM on a detached thread so we don't ANR the main looper.
        // launcher.stop() begins with proc.destroy(); the remaining delay is
        // just waiting for the process to exit. We don't need to block onDestroy
        // for that — the thread will finish or time out on its own.
        if (::launcher.isInitialized && launcher.isRunning) {
            thread(start = true, isDaemon = true, name = "SyncthingShutdown") {
                runBlocking {
                    try {
                        withTimeout(5_000) { launcher.stop() }
                    } catch (e: TimeoutCancellationException) {
                        Log.w(TAG, "Stop timed out; binary may be orphaned", e)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error stopping binary in onDestroy", e)
                    }
                }
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
                if (e is CancellationException) throw e
                Log.w(TAG, "Constraint gate read failed; proceeding", e)
            }
            try {
                // Bootstrap config on first run
                val generatedConfig = !bootstrapper.configExists
                if (generatedConfig) {
                    Log.i(TAG, "First run — generating config")
                    launcher.generateConfig()
                }

                // Re-patch every start. Users can carry old/bad config forward,
                // and without this the UI can point at localhost:8384 while the
                // daemon GUI is disabled, TLS-only, or bound elsewhere.
                val deviceId = try {
                    launcher.getDeviceId()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Could not read device ID before start; patching config without local name", e)
                    null
                }
                val preferredDeviceName = try {
                    settings.deviceName.first().takeIf { it.isNotBlank() }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.w(TAG, "Could not read preferred device name", e)
                    null
                }
                bootstrapper.patchConfig(
                    localDeviceId = deviceId,
                    preferredDeviceName = preferredDeviceName,
                    forcePreferredDeviceName = generatedConfig,
                )

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
                        // await() rethrows if the Deferred completed with an exception;
                        // wrap it so a launcher crash here doesn't unwind the error-handling path.
                        val exitCode = try {
                            processJob.await()
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Log.w(TAG, "Process job failed with exception before API was ready", e)
                            -1
                        }
                        Log.w(TAG, "Syncthing exited before API became ready with code $exitCode")
                    }
                    stopSelf()
                    return@launch
                }

                val running = RunState.Running(apiKey, port)
                _state.value = running
                updateNotification(running)

                val exitCode = processJob.await()
                val pauseReason = requestedPauseReason
                // A requested pause owns the process exit regardless of the
                // exact signal observed. During a network handoff SIGPIPE (141)
                // can win the race with our SIGTERM; it must not auto-restart.
                if (pauseReason != null) {
                    requestedPauseReason = null
                    val paused = RunState.Paused(pauseReason)
                    _state.value = paused
                    updateNotification(paused)
                    return@launch
                }
                val newState = launcher.interpretExitCode(exitCode)
                _state.value = newState

                when (newState) {
                    is RunState.Crashed -> {
                        notifications.showCrashedNotification(exitCode, newState.reason)
                        stopSelf()
                    }

                    is RunState.Starting -> {
                        // A recoverable exit requested a restart. Schedule on Main
                        // so the current job unwinds before startSyncthing() mutates
                        // job state.
                        Log.i(TAG, "Restart requested by Syncthing")
                        serviceScope.launch(Dispatchers.Main) {
                            syncthingJob = null
                            startSyncthing()
                        }
                    }

                    else -> stopSelf()
                }
            } catch (e: CancellationException) {
                throw e
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
                        if (e is CancellationException) throw e
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

    private fun stopSyncthing(suppressFutureAutoStarts: Boolean) {
        serviceScope.launch {
            if (suppressFutureAutoStarts) {
                settings.setStartSuppressedByUser(true)
            }
            if (launcher.isRunning) {
                launcher.stop()
            }
            _state.value = RunState.Stopped
            syncthingJob?.cancel()
            syncthingJob = null
            stopSelf()
        }
    }

    private fun pauseSyncthing(reason: String, suppressFutureAutoStarts: Boolean = false) {
        serviceScope.launch {
            pauseInProgress = true
            try {
                requestedPauseReason = reason
                if (suppressFutureAutoStarts) {
                    settings.setStartSuppressedByUser(true)
                }
                if (launcher.isRunning) {
                    launcher.stop()
                }
                syncthingJob?.cancel()
                syncthingJob = null
                requestedPauseReason = null

                // A network hand-off can satisfy the constraints again while
                // launcher.stop() is still waiting for the old process. Do not
                // lose that edge: resume as soon as the pause operation finishes.
                if (shouldResumeAfterPause(suppressFutureAutoStarts, pausedByConstraint)) {
                    Log.i(TAG, "Constraint pause completed after constraints recovered; auto-resuming")
                    startSyncthing()
                } else {
                    val paused = RunState.Paused(reason)
                    _state.value = paused
                    updateNotification(paused)
                }
            } finally {
                pauseInProgress = false
            }
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

internal fun shouldResumeAfterPause(
    suppressFutureAutoStarts: Boolean,
    pausedByConstraint: Boolean,
): Boolean = !suppressFutureAutoStarts && !pausedByConstraint

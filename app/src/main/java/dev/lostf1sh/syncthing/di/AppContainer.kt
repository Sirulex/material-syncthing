package dev.lostf1sh.syncthing.di

import android.content.Context
import android.util.Log
import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.api.events.EventStream
import dev.lostf1sh.syncthing.data.ConflictDetector
import dev.lostf1sh.syncthing.data.DeviceRepository
import dev.lostf1sh.syncthing.data.FolderCondition
import dev.lostf1sh.syncthing.data.parseFolderConditions
import dev.lostf1sh.syncthing.data.EventRepository
import dev.lostf1sh.syncthing.data.FolderRepository
import dev.lostf1sh.syncthing.data.HealthAggregator
import dev.lostf1sh.syncthing.data.model.BandwidthSample
import dev.lostf1sh.syncthing.data.NotificationPolicy
import dev.lostf1sh.syncthing.data.SettingsStore
import dev.lostf1sh.syncthing.data.SyncConstraints
import dev.lostf1sh.syncthing.data.SystemRepository
import dev.lostf1sh.syncthing.native.NativeLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class AppContainer(private val appContext: Context) {

    private companion object {
        const val TAG = "AppContainer"
        const val DIAGNOSTIC_THROTTLE_MS = 5_000L
    }

    val settingsStore = SettingsStore(appContext)
    val syncConstraints = SyncConstraints(appContext)
    val appState = AppState()
    private val nativeLauncher = NativeLauncher.fromContext(appContext)
    private val notificationPolicy = NotificationPolicy(appContext)

    /** Process-scope for collectors; survives Activity/Compose lifecycles. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    init {
        appScope.launch(Dispatchers.IO) {
            try {
                appState.setLocalDeviceId(nativeLauncher.getDeviceId())
            } catch (_: Exception) {
            }
        }
        appScope.launch {
            settingsStore.notifySyncComplete.collect {
                notificationPolicy.notifySyncComplete = it
            }
        }
        appScope.launch {
            settingsStore.notifyDeviceConnected.collect {
                notificationPolicy.notifyDeviceConnected = it
            }
        }
        appScope.launch {
            settingsStore.notifyErrors.collect {
                notificationPolicy.notifyErrors = it
            }
        }
    }

    /**
     * Bundle of everything that shares a single SyncthingClient lifetime.
     * Creating repositories here — not in per-call getters — gives the rest
     * of the app a stable single source of truth.
     */
    class ClientHandles internal constructor(
        val client: SyncthingClient,
        val folderRepository: FolderRepository,
        val deviceRepository: DeviceRepository,
        val systemRepository: SystemRepository,
        val eventRepository: EventRepository,
        val port: Int,
    )

    private val lock = Any()
    @Volatile
    private var handles: ClientHandles? = null
    private var collectorJob: Job? = null

    // For bandwidth delta computation — cumulative totals + wall-clock at the
    // moment we last sampled.
    private var lastTotalIn: Long = -1
    private var lastTotalOut: Long = -1
    private var lastSampleAt: Long = 0
    private var lastDiagnostic: String? = null
    private var lastDiagnosticAt: Long = 0

    val client: SyncthingClient? get() = handles?.client
    val folderRepository: FolderRepository? get() = handles?.folderRepository
    val deviceRepository: DeviceRepository? get() = handles?.deviceRepository
    val systemRepository: SystemRepository? get() = handles?.systemRepository
    val eventRepository: EventRepository? get() = handles?.eventRepository

    fun initClient(apiKey: String, port: Int = 8384) {
        synchronized(lock) {
            handles?.let {
                it.eventRepository.stop()
                it.client.close()
            }
            collectorJob?.cancel()
            val newClient = SyncthingClient(
                baseUrl = "http://127.0.0.1:$port",
                apiKey = apiKey,
            )
            val h = ClientHandles(
                client = newClient,
                folderRepository = FolderRepository(newClient),
                deviceRepository = DeviceRepository(newClient),
                systemRepository = SystemRepository(newClient),
                eventRepository = EventRepository(EventStream(newClient)),
                port = port,
            )
            handles = h
            collectorJob = appScope.launch { runCollectors(h) }
        }
    }

    fun tearDown() {
        synchronized(lock) {
            collectorJob?.cancel()
            collectorJob = null
            handles?.let {
                it.eventRepository.stop()
                it.client.close()
            }
            handles = null
            lastTotalIn = -1
            lastTotalOut = -1
            lastSampleAt = 0
            appState.reset()
        }
    }

    private suspend fun runCollectors(h: ClientHandles) = coroutineScope {
        waitForApiReady(h)

        // Identify the local device once; myID is stable for the process lifetime.
        launch {
            try {
                appState.setLocalDeviceId(h.systemRepository.status().myID)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportDiagnostic("Could not read local Syncthing device ID", e)
            }
        }

        // Event stream — live state/device/config updates.
        h.eventRepository.start(this)

        // Forward all events to the notification policy.
        launch {
            h.eventRepository.events.collect { event ->
                notificationPolicy.onEvent(event)
            }
        }

        // Collect recent logcat output for diagnostics viewer.
        launch {
            collectLogs()
        }

        launch {
            h.eventRepository.allFolderStates().collect { (folderId, state) ->
                appState.updateFolderState(folderId, state)
                enforceFolderConditions(h)
            }
        }

        launch {
            h.eventRepository.pendingDevicesChanged().collect {
                try { appState.setPendingDevices(h.client.pendingDevices()) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { reportDiagnostic("Could not refresh pending devices", e) }
            }
        }

        launch {
            h.eventRepository.deviceConnections().collect { (deviceId, connected) ->
                appState.updateDeviceConnection(deviceId, connected)
            }
        }

        launch {
            h.eventRepository.configChanges().collect {
                refreshConfig(h)
            }
        }

        // Recent changes feed — collect ItemFinished events.
        launch {
            h.eventRepository.recentChanges().collect { event ->
                appState.pushRecentChange(
                    RecentChangeItem(
                        folderId = event.folderId,
                        path = event.item,
                        action = event.action,
                        timestamp = event.time,
                        error = event.error,
                    )
                )
            }
        }

        // Periodic refresh — folders, statuses, connections, pending, health.
        launch {
            while (coroutineContext.isActive) {
                refreshAll(h)
                delay(3_000)
            }
        }

        // Conflict scan is heavier (filesystem walk); keep it on a slower
        // cadence AND run on IO — walkTopDown() blocks the thread, which would
        // otherwise jank/ANR the Main dispatcher.
        launch(Dispatchers.IO) {
            while (coroutineContext.isActive) {
                refreshConflicts()
                delay(30_000)
            }
        }

        // Per-folder / per-device stats refresh every 30s.
        launch {
            while (coroutineContext.isActive) {
                try { appState.setFolderStats(h.client.folderStats()) } catch (_: Exception) { }
                try { appState.setDeviceStats(h.client.deviceStats()) } catch (_: Exception) { }
                delay(30_000)
            }
        }
    }

    private suspend fun waitForApiReady(h: ClientHandles) {
        var attempts = 0
        while (coroutineContext.isActive) {
            try {
                if (h.client.ping().ping == "pong") {
                    clearDiagnostic()
                    return
                }
                if (attempts % 10 == 0) {
                    reportDiagnostic("Syncthing API on 127.0.0.1:${h.port} did not return pong")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempts % 10 == 0) {
                    reportDiagnostic("Syncthing API is not reachable on 127.0.0.1:${h.port}", e)
                }
            }
            attempts += 1
            delay(500)
        }
    }

    private fun reportDiagnostic(message: String, error: Throwable? = null) {
        val detail = error?.message?.takeIf { it.isNotBlank() } ?: error?.javaClass?.simpleName
        val full = if (detail == null) message else "$message: $detail"
        val now = System.currentTimeMillis()
        if (full == lastDiagnostic && now - lastDiagnosticAt < DIAGNOSTIC_THROTTLE_MS) return
        lastDiagnostic = full
        lastDiagnosticAt = now
        if (error == null) {
            Log.w(TAG, full)
        } else {
            Log.w(TAG, full, error)
        }
        appState.setDiagnostic(full)
        appState.pushLog("App: $full")
    }

    private fun clearDiagnostic() {
        if (lastDiagnostic != null || appState.diagnostic.value != null) {
            Log.i(TAG, "Syncthing API is reachable")
        }
        lastDiagnostic = null
        lastDiagnosticAt = 0
        appState.setDiagnostic(null)
    }

    private fun recordBandwidthSample(totalIn: Long, totalOut: Long) {
        val now = System.currentTimeMillis()
        if (lastTotalIn < 0) {
            // First sample — no delta yet.
            lastTotalIn = totalIn
            lastTotalOut = totalOut
            lastSampleAt = now
            return
        }
        val elapsedSec = ((now - lastSampleAt) / 1000.0).coerceAtLeast(0.5)
        val deltaIn = (totalIn - lastTotalIn).coerceAtLeast(0L)
        val deltaOut = (totalOut - lastTotalOut).coerceAtLeast(0L)
        appState.pushBandwidthSample(
            BandwidthSample(
                timestamp = now,
                inBytesPerSec = (deltaIn / elapsedSec).toLong(),
                outBytesPerSec = (deltaOut / elapsedSec).toLong(),
            )
        )
        lastTotalIn = totalIn
        lastTotalOut = totalOut
        lastSampleAt = now
    }

    private suspend fun collectLogs() {
        withContext(Dispatchers.IO) {
            val seen = LinkedHashSet<String>()
            while (coroutineContext.isActive) {
                try {
                    val process = Runtime.getRuntime().exec(
                        "logcat -d -t 200 -v threadtime AppContainer:V EventStream:V SyncthingService:D Syncthing:D lostf1sh:D *:S"
                    )
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (seen.add(line)) {
                                appState.pushLog(line)
                                if (seen.size > 500) {
                                    val iterator = seen.iterator()
                                    if (iterator.hasNext()) {
                                        iterator.next()
                                        iterator.remove()
                                    }
                                }
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                delay(10_000)
            }
        }
    }

    /**
     * Evaluate per-folder sync conditions and pause/resume folders as needed.
     * Called whenever constraints or folder states change.
     */
    private suspend fun enforceFolderConditions(h: ClientHandles) {
        val raw = try {
            settingsStore.folderConditions.first()
        } catch (_: Exception) { return }
        val conditions = parseFolderConditions(raw)
        if (conditions.isEmpty()) return

        val cm = appContext.getSystemService(android.content.Context.CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val activeCaps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isWifi = activeCaps?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val bm = appContext.getSystemService(android.content.Context.BATTERY_SERVICE)
            as android.os.BatteryManager
        val isCharging = bm.isCharging

        val folders = appState.folders.value
        for (folder in folders) {
            val c = conditions[folder.id] ?: continue
            val shouldPause = (c.wifiOnly && !isWifi) || (c.chargingOnly && !isCharging)
            if (shouldPause && !folder.paused) {
                try { h.client.pauseFolder(folder.id) } catch (_: Exception) { }
            } else if (!shouldPause && folder.paused) {
                try { h.client.resumeFolder(folder.id) } catch (_: Exception) { }
            }
        }
    }

    private fun refreshConflicts() {
        val folders = appState.folders.value
        if (folders.isEmpty()) {
            appState.setConflicts(emptyList())
            return
        }
        val all = folders.flatMap { folder ->
            if (folder.path.isBlank()) emptyList()
            else ConflictDetector.scan(folder.id, folder.path)
        }
        appState.setConflicts(all)
        if (all.isNotEmpty()) {
            notificationPolicy.updateFolderLabels(folders.associate { it.id to it.label.ifBlank { it.id } })
            notificationPolicy.onConflictsDetected(all)
        }
    }

    private suspend fun refreshConfig(h: ClientHandles) {
        try {
            val folders = h.folderRepository.folders()
            appState.setFolders(folders)
            appState.setDevices(h.deviceRepository.devices())
            notificationPolicy.updateFolderLabels(folders.associate { it.id to it.label.ifBlank { it.id } })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportDiagnostic("Could not refresh Syncthing config", e)
        }
    }

    private suspend fun refreshAll(h: ClientHandles) {
        try {
            val folders = h.folderRepository.folders()
            val devices = h.deviceRepository.devices()
            appState.setFolders(folders)
            appState.setDevices(devices)
            notificationPolicy.updateFolderLabels(folders.associate { it.id to it.label.ifBlank { it.id } })
            val systemStatus = h.systemRepository.status()
            appState.setSystemStatus(systemStatus)
            appState.setLocalDeviceId(systemStatus.myID)

            val statuses = mutableMapOf<String, dev.lostf1sh.syncthing.api.dto.FolderStatus>()
            val completions = mutableMapOf<String, dev.lostf1sh.syncthing.api.dto.FolderCompletionInfo>()
            for (folder in folders) {
                try {
                    val s = h.folderRepository.folderStatus(folder.id)
                    statuses[folder.id] = s
                    appState.updateFolderState(folder.id, s.state)
                } catch (_: Exception) { }
                for (device in folder.devices) {
                    if (device.deviceID == systemStatus.myID) continue
                    try {
                        completions["${folder.id}:${device.deviceID}"] = h.client.folderCompletion(
                            folderId = folder.id,
                            deviceId = device.deviceID,
                        )
                    } catch (_: Exception) { }
                }
            }
            appState.setFolderStatuses(statuses)

            try {
                val conns = h.systemRepository.connections()
                appState.setConnections(conns)
                recordBandwidthSample(conns.total.inBytesTotal, conns.total.outBytesTotal)
            } catch (_: Exception) { }

            val health = HealthAggregator.aggregate(
                folders = folders,
                folderStates = appState.folderStates.value,
                folderStatuses = statuses,
                folderCompletions = completions,
                deviceCount = devices.size,
                connectedDevices = appState.deviceConnections.value.count { it.value },
            )
            appState.setHealth(health)
            clearDiagnostic()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportDiagnostic("Could not refresh Syncthing state", e)
        }

        try {
            appState.setPendingFolders(h.client.pendingFolders())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportDiagnostic("Could not refresh pending folders", e)
        }

        try {
            appState.setPendingDevices(h.client.pendingDevices())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            reportDiagnostic("Could not refresh pending devices", e)
        }
    }
}

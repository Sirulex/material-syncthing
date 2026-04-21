package dev.lostf1sh.syncthing.di

import android.content.Context
import dev.lostf1sh.syncthing.api.SyncthingClient
import dev.lostf1sh.syncthing.api.events.EventStream
import dev.lostf1sh.syncthing.data.ConflictDetector
import dev.lostf1sh.syncthing.data.DeviceRepository
import dev.lostf1sh.syncthing.data.EventRepository
import dev.lostf1sh.syncthing.data.FolderRepository
import dev.lostf1sh.syncthing.data.HealthAggregator
import dev.lostf1sh.syncthing.data.model.BandwidthSample
import dev.lostf1sh.syncthing.data.NotificationPolicy
import dev.lostf1sh.syncthing.data.SettingsStore
import dev.lostf1sh.syncthing.data.SyncConstraints
import dev.lostf1sh.syncthing.data.SystemRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

class AppContainer(private val appContext: Context) {

    val settingsStore = SettingsStore(appContext)
    val syncConstraints = SyncConstraints(appContext)
    val appState = AppState()

    /** Process-scope for collectors; survives Activity/Compose lifecycles. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

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
        // Identify the local device once; myID is stable for the process lifetime.
        launch {
            try {
                appState.setLocalDeviceId(h.systemRepository.status().myID)
            } catch (_: Exception) { }
        }

        // Event stream — live state/device/config updates.
        h.eventRepository.start(this)

        launch {
            h.eventRepository.allFolderStates().collect { (folderId, state) ->
                appState.updateFolderState(folderId, state)
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
    }

    private suspend fun refreshConfig(h: ClientHandles) {
        try {
            appState.setFolders(h.folderRepository.folders())
            appState.setDevices(h.deviceRepository.devices())
        } catch (_: Exception) { }
    }

    private suspend fun refreshAll(h: ClientHandles) {
        try {
            val folders = h.folderRepository.folders()
            val devices = h.deviceRepository.devices()
            appState.setFolders(folders)
            appState.setDevices(devices)

            val statuses = mutableMapOf<String, dev.lostf1sh.syncthing.api.dto.FolderStatus>()
            for (folder in folders) {
                try {
                    val s = h.folderRepository.folderStatus(folder.id)
                    statuses[folder.id] = s
                    appState.updateFolderState(folder.id, s.state)
                } catch (_: Exception) { }
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
                deviceCount = devices.size,
                connectedDevices = appState.deviceConnections.value.count { it.value },
            )
            appState.setHealth(health)
        } catch (_: Exception) { }

        try {
            appState.setPendingFolders(h.client.pendingFolders())
        } catch (_: Exception) { }
    }
}

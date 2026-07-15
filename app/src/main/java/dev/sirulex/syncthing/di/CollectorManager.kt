package dev.sirulex.syncthing.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import dev.sirulex.syncthing.data.ConflictDetector
import dev.sirulex.syncthing.data.HealthAggregator
import dev.sirulex.syncthing.data.NotificationPolicy
import dev.sirulex.syncthing.data.SettingsStore
import dev.sirulex.syncthing.data.parseFolderConditions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class CollectorManager(
    private val appContext: Context,
    private val appScope: CoroutineScope,
    private val appState: AppState,
    private val settingsStore: SettingsStore,
    private val notificationPolicy: NotificationPolicy,
    private val bandwidthTracker: BandwidthTracker,
) {
    private companion object {
        const val TAG = "CollectorManager"
        const val DIAGNOSTIC_THROTTLE_MS = 5_000L
        const val BANDWIDTH_POLL_MS = 250L
        const val CONFLICT_SCAN_DEBOUNCE_MS = 10_000L
        const val CONFLICT_SCAN_MIN_INTERVAL_MS = 2 * 60_000L
    }

    private val lock = Any()
    private var collectorJob: Job? = null
    private var conflictRefreshJob: Job? = null
    private var lastDiagnostic: String? = null
    private var lastDiagnosticAt: Long = 0
    private var lastConflictScanAt: Long = 0
    private var initialConflictScanRequested = false

    fun start(handles: ClientHandles) {
        collectorJob?.cancel()
        cancelConflictRefresh()
        initialConflictScanRequested = false
        lastConflictScanAt = 0
        collectorJob = appScope.launch { runCollectors(handles) }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        cancelConflictRefresh()
        initialConflictScanRequested = false
        lastConflictScanAt = 0
        clearDiagnosticState()
    }

    private suspend fun runCollectors(h: ClientHandles) = coroutineScope {
        waitForApiReady(h)

        launch {
            try {
                appState.setLocalDeviceId(h.systemRepository.status().myID)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportDiagnostic("Could not read local Syncthing device ID", e)
            }
        }

        launch {
            h.eventRepository.events.collect { event ->
                notificationPolicy.onEvent(event)
            }
        }

        launch { collectLogs() }

        launch {
            h.eventRepository.allFolderStates().collect { (folderId, state) ->
                appState.updateFolderState(folderId, state)
                enforceFolderConditions(h)
                if (state == "idle" || state == "error") {
                    scheduleConflictRefresh()
                }
            }
        }

        launch {
            h.eventRepository.pendingDevicesChanged().collect {
                try {
                    appState.setPendingDevices(h.client.pendingDevices())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportDiagnostic("Could not refresh pending devices", e)
                }
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
                scheduleConflictRefresh(delayMs = 0)
            }
        }

        launch {
            h.eventRepository.recentChanges().collect { event ->
                appState.pushRecentChange(
                    RecentChangeItem(
                        folderId = event.folderId,
                        path = event.path,
                        action = event.action,
                        timestamp = event.time,
                        error = null,
                    )
                )
                scheduleConflictRefresh()
            }
        }

        // Start upstream only after all SharedFlow subscribers above have been
        // installed. This preserves the initial buffered event batch returned
        // for since=0, which supplies the first Recent Changes snapshot.
        h.eventRepository.start(this)

        launch {
            while (coroutineContext.isActive) {
                refreshAll(h)
                delay(3_000)
            }
        }

        launch {
            while (coroutineContext.isActive) {
                refreshConnections(h)
                delay(BANDWIDTH_POLL_MS)
            }
        }

        launch {
            while (coroutineContext.isActive) {
                try {
                    appState.setFolderStats(h.client.folderStats())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                try {
                    appState.setDeviceStats(h.client.deviceStats())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
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
        synchronized(lock) {
            if (full == lastDiagnostic && now - lastDiagnosticAt < DIAGNOSTIC_THROTTLE_MS) return
            lastDiagnostic = full
            lastDiagnosticAt = now
        }
        if (error == null) {
            Log.w(TAG, full)
        } else {
            Log.w(TAG, full, error)
        }
        appState.setDiagnostic(full)
        appState.pushLog("App: $full")
    }

    private fun clearDiagnostic() {
        val hadDiagnostic = clearDiagnosticState()
        if (hadDiagnostic || appState.diagnostic.value != null) {
            Log.i(TAG, "Syncthing API is reachable")
        }
        appState.setDiagnostic(null)
    }

    private fun cancelConflictRefresh() = synchronized(lock) {
        conflictRefreshJob?.cancel()
        conflictRefreshJob = null
    }

    private fun scheduleConflictRefresh(delayMs: Long = CONFLICT_SCAN_DEBOUNCE_MS) {
        synchronized(lock) {
            conflictRefreshJob?.cancel()
            conflictRefreshJob = appScope.launch(Dispatchers.IO) {
                val job = coroutineContext[Job]
                val now = System.currentTimeMillis()
                val remaining = synchronized(lock) {
                    (lastConflictScanAt + CONFLICT_SCAN_MIN_INTERVAL_MS - now).coerceAtLeast(0L)
                }
                delay(maxOf(delayMs, remaining))
                try {
                    refreshConflicts()
                } finally {
                    synchronized(lock) {
                        lastConflictScanAt = System.currentTimeMillis()
                        if (conflictRefreshJob === job) {
                            conflictRefreshJob = null
                        }
                    }
                }
            }
        }
    }

    private fun clearDiagnosticState(): Boolean = synchronized(lock) {
        val hadDiagnostic = lastDiagnostic != null
        lastDiagnostic = null
        lastDiagnosticAt = 0
        hadDiagnostic
    }

    private suspend fun collectLogs() {
        withContext(Dispatchers.IO) {
            val seen = LinkedHashSet<String>()
            while (coroutineContext.isActive) {
                try {
                    val process = Runtime.getRuntime().exec(
                        "logcat -d -t 200 -v threadtime CollectorManager:V AppContainer:V EventStream:V SyncthingService:D Syncthing:D sirulex:D *:S"
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

    private suspend fun enforceFolderConditions(h: ClientHandles) {
        val raw = try {
            settingsStore.folderConditions.first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        val conditions = parseFolderConditions(raw)
        if (conditions.isEmpty()) return

        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork
        val activeCaps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val isWifi = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false
        val bm = appContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val isCharging = bm.isCharging

        val folders = appState.folders.value
        for (folder in folders) {
            val c = conditions[folder.id] ?: continue
            val shouldPause = (c.wifiOnly && !isWifi) || (c.chargingOnly && !isCharging)
            if (shouldPause && !folder.paused) {
                try {
                    h.client.pauseFolder(folder.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            } else if (!shouldPause && folder.paused) {
                try {
                    h.client.resumeFolder(folder.id)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
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
            if (folder.path.isBlank()) emptyList() else ConflictDetector.scan(folder.id, folder.path)
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
            if (!initialConflictScanRequested) {
                initialConflictScanRequested = true
                scheduleConflictRefresh(delayMs = 0)
            }
            notificationPolicy.updateFolderLabels(folders.associate { it.id to it.label.ifBlank { it.id } })
            val systemStatus = h.systemRepository.status()
            appState.setSystemStatus(systemStatus)
            appState.setLocalDeviceId(systemStatus.myID)

            val statuses = mutableMapOf<String, dev.sirulex.syncthing.api.dto.FolderStatus>()
            val completions = mutableMapOf<String, dev.sirulex.syncthing.api.dto.FolderCompletionInfo>()
            for (folder in folders) {
                try {
                    val s = h.folderRepository.folderStatus(folder.id)
                    statuses[folder.id] = s
                    appState.updateFolderState(folder.id, s.state)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
                for (device in folder.devices) {
                    if (device.deviceID == systemStatus.myID) continue
                    try {
                        completions["${folder.id}:${device.deviceID}"] = h.client.folderCompletion(
                            folderId = folder.id,
                            deviceId = device.deviceID,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                    }
                }
            }
            appState.setFolderStatuses(statuses)

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

    private suspend fun refreshConnections(h: ClientHandles) {
        try {
            val connections = h.systemRepository.connections()
            appState.setConnections(connections)
            bandwidthTracker.record(
                totalIn = connections.total.inBytesTotal,
                totalOut = connections.total.outBytesTotal,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }
}

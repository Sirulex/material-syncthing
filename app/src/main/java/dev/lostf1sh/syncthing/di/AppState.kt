package dev.lostf1sh.syncthing.di

import dev.lostf1sh.syncthing.api.dto.Connections
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import dev.lostf1sh.syncthing.api.dto.PendingDevice
import dev.lostf1sh.syncthing.api.dto.PendingFolder
import dev.lostf1sh.syncthing.api.dto.DeviceStats
import dev.lostf1sh.syncthing.api.dto.FolderStats
import dev.lostf1sh.syncthing.api.dto.SystemStatus
import dev.lostf1sh.syncthing.data.model.BandwidthSample
import dev.lostf1sh.syncthing.data.model.ConflictItem
import dev.lostf1sh.syncthing.data.model.SyncHealth
import dev.lostf1sh.syncthing.data.model.SyncIssue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class RecentChangeItem(
    val folderId: String,
    val path: String,
    val action: String,
    val timestamp: String,
    val error: String?,
)

/**
 * Process-wide snapshot of Syncthing state. Populated by [AppContainer]'s
 * collector loop whenever the service is running. Read by UI via
 * `collectAsStateWithLifecycle`, and by widgets / tiles from the process scope.
 */
class AppState {
    private val _folders = MutableStateFlow<List<Folder>>(emptyList())
    val folders: StateFlow<List<Folder>> = _folders.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _folderStatuses = MutableStateFlow<Map<String, FolderStatus>>(emptyMap())
    val folderStatuses: StateFlow<Map<String, FolderStatus>> = _folderStatuses.asStateFlow()

    private val _folderStates = MutableStateFlow<Map<String, String>>(emptyMap())
    val folderStates: StateFlow<Map<String, String>> = _folderStates.asStateFlow()

    private val _deviceConnections = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val deviceConnections: StateFlow<Map<String, Boolean>> = _deviceConnections.asStateFlow()

    private val _connections = MutableStateFlow<Connections?>(null)
    val connections: StateFlow<Connections?> = _connections.asStateFlow()

    private val _health = MutableStateFlow<SyncHealth?>(null)
    val health: StateFlow<SyncHealth?> = _health.asStateFlow()

    private val _issues = MutableStateFlow<List<SyncIssue>>(emptyList())
    val issues: StateFlow<List<SyncIssue>> = _issues.asStateFlow()

    private val _conflicts = MutableStateFlow<List<ConflictItem>>(emptyList())
    val conflicts: StateFlow<List<ConflictItem>> = _conflicts.asStateFlow()

    private val _localDeviceId = MutableStateFlow<String?>(null)
    val localDeviceId: StateFlow<String?> = _localDeviceId.asStateFlow()

    private val _pendingFolders = MutableStateFlow<Map<String, PendingFolder>>(emptyMap())
    val pendingFolders: StateFlow<Map<String, PendingFolder>> = _pendingFolders.asStateFlow()

    private val _pendingDevices = MutableStateFlow<Map<String, PendingDevice>>(emptyMap())
    val pendingDevices: StateFlow<Map<String, PendingDevice>> = _pendingDevices.asStateFlow()

    private val _bandwidthHistory = MutableStateFlow<List<BandwidthSample>>(emptyList())
    val bandwidthHistory: StateFlow<List<BandwidthSample>> = _bandwidthHistory.asStateFlow()

    private val _folderStats = MutableStateFlow<Map<String, FolderStats>>(emptyMap())
    val folderStats: StateFlow<Map<String, FolderStats>> = _folderStats.asStateFlow()

    private val _deviceStats = MutableStateFlow<Map<String, DeviceStats>>(emptyMap())
    val deviceStats: StateFlow<Map<String, DeviceStats>> = _deviceStats.asStateFlow()

    private val _recentChanges = MutableStateFlow<List<RecentChangeItem>>(emptyList())
    val recentChanges: StateFlow<List<RecentChangeItem>> = _recentChanges.asStateFlow()

    private val _systemStatus = MutableStateFlow<SystemStatus?>(null)
    val systemStatus: StateFlow<SystemStatus?> = _systemStatus.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    fun setFolders(list: List<Folder>) { _folders.value = list }
    fun setDevices(list: List<Device>) { _devices.value = list }
    fun setFolderStatuses(map: Map<String, FolderStatus>) { _folderStatuses.value = map }
    fun setConnections(c: Connections?) {
        _connections.value = c
        if (c != null) {
            _deviceConnections.value = c.connections.mapValues { it.value.connected }
        }
    }
    fun setHealth(h: SyncHealth?) {
        _health.value = h
        _issues.value = h?.issues ?: emptyList()
    }
    fun setConflicts(list: List<ConflictItem>) { _conflicts.value = list }
    fun setLocalDeviceId(id: String?) { _localDeviceId.value = id }
    fun setPendingFolders(map: Map<String, PendingFolder>) { _pendingFolders.value = map }
    fun setPendingDevices(map: Map<String, PendingDevice>) { _pendingDevices.value = map }
    fun setFolderStats(map: Map<String, FolderStats>) { _folderStats.value = map }
    fun setDeviceStats(map: Map<String, DeviceStats>) { _deviceStats.value = map }
    fun setSystemStatus(s: SystemStatus?) { _systemStatus.value = s }
    fun pushLog(line: String) { _logs.update { (it + line).takeLast(500) } }

    fun pushRecentChange(item: RecentChangeItem) {
        _recentChanges.update { (listOf(item) + it).take(MAX_RECENT_CHANGES) }
    }

    fun pushBandwidthSample(sample: BandwidthSample) {
        _bandwidthHistory.update { (it + sample).takeLast(MAX_BANDWIDTH_SAMPLES) }
    }

    companion object {
        const val MAX_BANDWIDTH_SAMPLES = 60
        const val MAX_RECENT_CHANGES = 100
    }

    fun updateFolderState(folderId: String, state: String) {
        _folderStates.update { it + (folderId to state) }
    }

    fun updateDeviceConnection(deviceId: String, connected: Boolean) {
        _deviceConnections.update { it + (deviceId to connected) }
    }

    /** Wipe everything — called on tearDown / service stop. */
    fun reset() {
        _folders.value = emptyList()
        _devices.value = emptyList()
        _folderStatuses.value = emptyMap()
        _folderStates.value = emptyMap()
        _deviceConnections.value = emptyMap()
        _connections.value = null
        _health.value = null
        _issues.value = emptyList()
        _conflicts.value = emptyList()
        _pendingFolders.value = emptyMap()
        _pendingDevices.value = emptyMap()
        _bandwidthHistory.value = emptyList()
        _folderStats.value = emptyMap()
        _deviceStats.value = emptyMap()
        _recentChanges.value = emptyList()
        _systemStatus.value = null
        _logs.value = emptyList()
    }
}

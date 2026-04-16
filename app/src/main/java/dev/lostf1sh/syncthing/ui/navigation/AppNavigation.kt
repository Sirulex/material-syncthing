package dev.lostf1sh.syncthing.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.lostf1sh.syncthing.SyncthingApp
import dev.lostf1sh.syncthing.api.dto.ConnectionInfo
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import dev.lostf1sh.syncthing.native.RunState
import dev.lostf1sh.syncthing.service.SyncthingService
import dev.lostf1sh.syncthing.api.dto.Device as DeviceDto
import dev.lostf1sh.syncthing.ui.devices.AddDeviceScreen
import dev.lostf1sh.syncthing.ui.devices.DeviceDetailScreen
import dev.lostf1sh.syncthing.ui.folders.AcceptFolderDialog
import dev.lostf1sh.syncthing.ui.folders.FolderDetailScreen
import dev.lostf1sh.syncthing.ui.folders.PendingFolderUi
import dev.lostf1sh.syncthing.ui.home.HomeScreen
import dev.lostf1sh.syncthing.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val serviceState by SyncthingService.state.collectAsState()

    // Live data
    var folders by remember { mutableStateOf(emptyList<Folder>()) }
    var devices by remember { mutableStateOf(emptyList<Device>()) }
    val folderStates = remember { mutableStateMapOf<String, String>() }
    val deviceConnections = remember { mutableStateMapOf<String, Boolean>() }
    var pendingFolder by remember { mutableStateOf<PendingFolderUi?>(null) }

    // Fetch data when service is running
    LaunchedEffect(serviceState) {
        val running = serviceState as? RunState.Running ?: return@LaunchedEffect
        val app = navController.context.applicationContext as SyncthingApp
        app.container.initClient(running.apiKey, running.port)
        val container = app.container

        // Initial fetch
        scope.launch {
            try {
                folders = container.folderRepository?.folders() ?: emptyList()
                devices = container.deviceRepository?.devices() ?: emptyList()
                // Fetch initial folder statuses
                folders.forEach { folder ->
                    try {
                        val status = container.folderRepository?.folderStatus(folder.id)
                        status?.let { folderStates[folder.id] = it.state }
                    } catch (_: Exception) { }
                }
                // Fetch initial connections
                try {
                    val conns = container.systemRepository?.connections()
                    conns?.connections?.forEach { (id, info) ->
                        deviceConnections[id] = info.connected
                    }
                } catch (_: Exception) { }
            } catch (_: Exception) { }
        }

        // Start event stream for live updates
        container.eventRepository?.start(scope)
        scope.launch {
            container.eventRepository?.allFolderStates()?.collect { (folderId, state) ->
                folderStates[folderId] = state
            }
        }
        scope.launch {
            container.eventRepository?.deviceConnections()?.collect { (deviceId, connected) ->
                deviceConnections[deviceId] = connected
            }
        }
        scope.launch {
            container.eventRepository?.configChanges()?.collect {
                try {
                    folders = container.folderRepository?.folders() ?: emptyList()
                    devices = container.deviceRepository?.devices() ?: emptyList()
                } catch (_: Exception) { }
            }
        }
        // Periodic refresh of folders, devices, statuses, and pending
        scope.launch {
            while (true) {
                delay(3_000)
                try {
                    folders = container.folderRepository?.folders() ?: emptyList()
                    devices = container.deviceRepository?.devices() ?: emptyList()
                    folders.forEach { folder ->
                        try {
                            val st = container.folderRepository?.folderStatus(folder.id)
                            st?.let { folderStates[folder.id] = it.state }
                        } catch (_: Exception) { }
                    }
                    val conns = container.systemRepository?.connections()
                    conns?.connections?.forEach { (id, info) ->
                        deviceConnections[id] = info.connected
                    }
                } catch (_: Exception) { }
                // Check pending folders
                try {
                    val pending = container.client?.pendingFolders() ?: emptyMap()
                    if (pending.isNotEmpty() && pendingFolder == null) {
                        val (folderId, info) = pending.entries.first()
                        val (deviceId, folderInfo) = info.offeredBy.entries.first()
                        val deviceName = devices.find { it.deviceID == deviceId }?.name ?: ""
                        pendingFolder = PendingFolderUi(
                            folderId = folderId,
                            label = folderInfo.label,
                            offeredByDevice = deviceId,
                            offeredByName = deviceName,
                        )
                    }
                } catch (_: Exception) { }
            }
        }
    }

    // Pending folder accept dialog
    pendingFolder?.let { pf ->
        AcceptFolderDialog(
            pending = pf,
            onAccept = { path ->
                scope.launch {
                    val app = navController.context.applicationContext as SyncthingApp
                    try {
                        app.container.client?.addFolder(Folder(
                            id = pf.folderId,
                            label = pf.label,
                            path = path,
                            devices = listOf(
                                dev.lostf1sh.syncthing.api.dto.FolderDevice(deviceID = pf.offeredByDevice),
                            ),
                        ))
                        app.container.client?.dismissPendingFolder(pf.folderId, pf.offeredByDevice)
                        folders = app.container.folderRepository?.folders() ?: emptyList()
                    } catch (_: Exception) { }
                }
                pendingFolder = null
            },
            onDismiss = {
                scope.launch {
                    val app = navController.context.applicationContext as SyncthingApp
                    try {
                        app.container.client?.dismissPendingFolder(pf.folderId, pf.offeredByDevice)
                    } catch (_: Exception) { }
                }
                pendingFolder = null
            },
        )
    }

    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen(
                folders = folders,
                folderStates = folderStates,
                devices = devices,
                deviceConnections = deviceConnections,
                onFolderClick = { navController.navigate(FolderRoute(it)) },
                onDeviceClick = { navController.navigate(DeviceRoute(it)) },
                onAddDevice = { navController.navigate(AddDeviceRoute()) },
                onScanQr = { /* ML Kit scanner launch */ },
                onSettingsClick = { navController.navigate(SettingsRoute) },
            )
        }
        composable<FolderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FolderRoute>()
            val folder = folders.find { it.id == route.id }

            var status by remember { mutableStateOf<FolderStatus?>(null) }
            LaunchedEffect(route.id, serviceState) {
                val running = serviceState as? RunState.Running ?: return@LaunchedEffect
                val app = navController.context.applicationContext as SyncthingApp
                try {
                    status = app.container.folderRepository?.folderStatus(route.id)
                } catch (_: Exception) { }
                // Live updates via events
                app.container.eventRepository?.folderState(route.id)?.collect {
                    try {
                        status = app.container.folderRepository?.folderStatus(route.id)
                    } catch (_: Exception) { }
                }
            }

            FolderDetailScreen(
                folder = folder,
                status = status,
                onBack = { navController.popBackStack() },
            )
        }
        composable<DeviceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<DeviceRoute>()
            val device = devices.find { it.deviceID == route.id }

            var connection by remember { mutableStateOf<ConnectionInfo?>(null) }
            LaunchedEffect(route.id, serviceState) {
                val running = serviceState as? RunState.Running ?: return@LaunchedEffect
                val app = navController.context.applicationContext as SyncthingApp
                try {
                    val conns = app.container.systemRepository?.connections()
                    connection = conns?.connections?.get(route.id)
                } catch (_: Exception) { }
                // Live updates via events
                app.container.eventRepository?.deviceConnections()?.collect { (id, _) ->
                    if (id == route.id) {
                        try {
                            val conns = app.container.systemRepository?.connections()
                            connection = conns?.connections?.get(route.id)
                        } catch (_: Exception) { }
                    }
                }
            }

            DeviceDetailScreen(
                device = device,
                connection = connection,
                onBack = { navController.popBackStack() },
            )
        }
        composable<AddDeviceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AddDeviceRoute>()
            AddDeviceScreen(
                initialDeviceId = route.prefillId,
                onAdd = { deviceId, name ->
                    val app = navController.context.applicationContext as SyncthingApp
                    val repo = app.container.deviceRepository
                        ?: return@AddDeviceScreen Result.failure(
                            Exception("Syncthing service not running")
                        )
                    try {
                        repo.addDevice(
                            DeviceDto(
                                deviceID = deviceId,
                                name = name,
                                addresses = listOf("dynamic"),
                            )
                        )
                        // Refresh device list
                        devices = repo.devices()
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                },
                onScanQr = { /* ML Kit scanner */ },
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            val app = navController.context.applicationContext as SyncthingApp
            SettingsScreen(
                settingsStore = app.container.settingsStore,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

package dev.lostf1sh.syncthing.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import dev.lostf1sh.syncthing.data.HealthAggregator
import dev.lostf1sh.syncthing.data.model.SyncHealth
import dev.lostf1sh.syncthing.data.model.SyncIssue
import dev.lostf1sh.syncthing.data.model.ConflictItem
import dev.lostf1sh.syncthing.ui.errors.ConflictScreen
import dev.lostf1sh.syncthing.ui.errors.ErrorCenterScreen
import dev.lostf1sh.syncthing.ui.home.HomeScreen
import dev.lostf1sh.syncthing.ui.onboarding.OnboardingScreen
import dev.lostf1sh.syncthing.ui.settings.DiagnosticsScreen
import dev.lostf1sh.syncthing.ui.settings.ProfilesScreen
import dev.lostf1sh.syncthing.ui.settings.SettingsScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val serviceState by SyncthingService.state.collectAsState()
    val app = navController.context.applicationContext as SyncthingApp
    val onboardingDone by app.container.settingsStore.onboardingComplete
        .collectAsState(initial = null)

    // Live data
    var folders by remember { mutableStateOf(emptyList<Folder>()) }
    var devices by remember { mutableStateOf(emptyList<Device>()) }
    val folderStates = remember { mutableStateMapOf<String, String>() }
    val deviceConnections = remember { mutableStateMapOf<String, Boolean>() }
    var pendingFolder by remember { mutableStateOf<PendingFolderUi?>(null) }
    val folderStatuses = remember { mutableStateMapOf<String, dev.lostf1sh.syncthing.api.dto.FolderStatus>() }
    var health by remember { mutableStateOf<SyncHealth?>(null) }
    var issues by remember { mutableStateOf(emptyList<SyncIssue>()) }

    // Fetch data when service is running
    LaunchedEffect(serviceState) {
        val running = serviceState as? RunState.Running ?: return@LaunchedEffect
        val app = navController.context.applicationContext as SyncthingApp
        app.container.initClient(running.apiKey, running.port)
        val container = app.container

        // Initial fetch
        launch {
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
        container.eventRepository?.start(this)
        launch {
            container.eventRepository?.allFolderStates()?.collect { (folderId, state) ->
                folderStates[folderId] = state
            }
        }
        launch {
            container.eventRepository?.deviceConnections()?.collect { (deviceId, connected) ->
                deviceConnections[deviceId] = connected
            }
        }
        launch {
            container.eventRepository?.configChanges()?.collect {
                try {
                    folders = container.folderRepository?.folders() ?: emptyList()
                    devices = container.deviceRepository?.devices() ?: emptyList()
                } catch (_: Exception) { }
            }
        }
        // Periodic refresh of folders, devices, statuses, and pending
        launch {
            while (true) {
                delay(3_000)
                try {
                    folders = container.folderRepository?.folders() ?: emptyList()
                    devices = container.deviceRepository?.devices() ?: emptyList()
                    folders.forEach { folder ->
                        try {
                            val st = container.folderRepository?.folderStatus(folder.id)
                            st?.let {
                                folderStates[folder.id] = it.state
                                folderStatuses[folder.id] = it
                            }
                        } catch (_: Exception) { }
                    }
                    val conns = container.systemRepository?.connections()
                    conns?.connections?.forEach { (id, info) ->
                        deviceConnections[id] = info.connected
                    }
                    // Aggregate health
                    val h = HealthAggregator.aggregate(
                        folders = folders,
                        folderStates = folderStates,
                        folderStatuses = folderStatuses,
                        deviceCount = devices.size,
                        connectedDevices = deviceConnections.count { it.value },
                    )
                    health = h
                    issues = h.issues
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

    val done = onboardingDone ?: return
    val startDest: Any = if (done) HomeRoute else OnboardingRoute

    // Expressive predictive-back transitions — nav-compose drives progress
    // from the system back gesture when enableOnBackInvokedCallback=true.
    val spatialSpec = tween<Float>(durationMillis = 350)
    NavHost(
        navController = navController,
        startDestination = startDest,
        enterTransition = {
            slideIntoContainer(
                towards = SlideDirection.Start,
                animationSpec = tween(350),
            ) + fadeIn(animationSpec = spatialSpec)
        },
        exitTransition = {
            slideOutOfContainer(
                towards = SlideDirection.Start,
                animationSpec = tween(350),
                targetOffset = { it / 6 },
            ) + fadeOut(animationSpec = spatialSpec)
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = SlideDirection.End,
                animationSpec = tween(350),
                initialOffset = { it / 6 },
            ) + fadeIn(animationSpec = spatialSpec)
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = SlideDirection.End,
                animationSpec = tween(350),
            ) + fadeOut(animationSpec = spatialSpec)
        },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                settingsStore = app.container.settingsStore,
                onComplete = {
                    navController.navigate(HomeRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
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
                health = health,
            )
        }
        composable<FolderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FolderRoute>()
            val folder = folders.find { it.id == route.id }

            var status by remember { mutableStateOf<FolderStatus?>(null) }
            val app = navController.context.applicationContext as SyncthingApp

            // Poll status every 2s for live progress
            LaunchedEffect(route.id, serviceState) {
                val running = serviceState as? RunState.Running ?: return@LaunchedEffect
                while (true) {
                    try {
                        status = app.container.folderRepository?.folderStatus(route.id)
                    } catch (_: Exception) { }
                    delay(2_000)
                }
            }

            FolderDetailScreen(
                folder = folder,
                status = status,
                onBack = { navController.popBackStack() },
                onPause = { id ->
                    scope.launch {
                        try {
                            app.container.client?.pauseFolder(id)
                            folders = app.container.folderRepository?.folders() ?: emptyList()
                        } catch (_: Exception) { }
                    }
                },
                onResume = { id ->
                    scope.launch {
                        try {
                            app.container.client?.resumeFolder(id)
                            folders = app.container.folderRepository?.folders() ?: emptyList()
                        } catch (_: Exception) { }
                    }
                },
                onRescan = { id ->
                    scope.launch {
                        try { app.container.client?.rescanFolder(id) }
                        catch (_: Exception) { }
                    }
                },
                onRemove = { id ->
                    scope.launch {
                        try {
                            app.container.client?.deleteFolder(id)
                        } catch (_: Exception) { return@launch }
                        try {
                            folders = app.container.folderRepository?.folders() ?: emptyList()
                        } catch (_: Exception) { }
                        navController.popBackStack()
                    }
                },
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
                onPause = { id ->
                    scope.launch {
                        try {
                            app.container.client?.pauseDevice(id)
                            devices = app.container.deviceRepository?.devices() ?: emptyList()
                        } catch (_: Exception) { }
                    }
                },
                onResume = { id ->
                    scope.launch {
                        try {
                            app.container.client?.resumeDevice(id)
                            devices = app.container.deviceRepository?.devices() ?: emptyList()
                        } catch (_: Exception) { }
                    }
                },
                onRemove = { id ->
                    scope.launch {
                        try {
                            app.container.client?.deleteDevice(id)
                        } catch (_: Exception) { return@launch }
                        try {
                            devices = app.container.deviceRepository?.devices() ?: emptyList()
                        } catch (_: Exception) { }
                        navController.popBackStack()
                    }
                },
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
        composable<ErrorCenterRoute> {
            ErrorCenterScreen(
                issues = issues,
                onBack = { navController.popBackStack() },
                onRescan = { id ->
                    scope.launch {
                        try { app.container.client?.rescanFolder(id) }
                        catch (_: Exception) { }
                    }
                },
            )
        }
        composable<ConflictRoute> {
            ConflictScreen(
                conflicts = emptyList(), // populated when conflict detection is wired
                onBack = { navController.popBackStack() },
            )
        }
        composable<ProfilesRoute> {
            ProfilesScreen(
                settingsStore = app.container.settingsStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable<DiagnosticsRoute> {
            DiagnosticsScreen(
                settingsStore = app.container.settingsStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                settingsStore = app.container.settingsStore,
                onBack = { navController.popBackStack() },
                onProfilesClick = { navController.navigate(ProfilesRoute) },
                onDiagnosticsClick = { navController.navigate(DiagnosticsRoute) },
                onErrorCenterClick = { navController.navigate(ErrorCenterRoute) },
            )
        }
    }
}

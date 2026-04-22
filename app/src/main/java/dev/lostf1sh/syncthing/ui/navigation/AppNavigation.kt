package dev.lostf1sh.syncthing.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navDeepLink
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.lostf1sh.syncthing.SyncthingApp
import dev.lostf1sh.syncthing.api.dto.ConnectionInfo
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderDevice
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import dev.lostf1sh.syncthing.native.RunState
import dev.lostf1sh.syncthing.service.SyncthingService
import dev.lostf1sh.syncthing.api.dto.Device as DeviceDto
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import dev.lostf1sh.syncthing.ui.devices.AddDeviceScreen
import dev.lostf1sh.syncthing.ui.devices.DeviceDetailScreen
import dev.lostf1sh.syncthing.ui.devices.EditDeviceScreen
import dev.lostf1sh.syncthing.ui.folders.AcceptFolderDialog
import dev.lostf1sh.syncthing.ui.folders.AddFolderScreen
import dev.lostf1sh.syncthing.ui.folders.FolderBrowserScreen
import dev.lostf1sh.syncthing.ui.folders.FolderDetailScreen
import dev.lostf1sh.syncthing.ui.folders.IgnoreEditorScreen
import dev.lostf1sh.syncthing.ui.folders.EditFolderScreen
import dev.lostf1sh.syncthing.ui.folders.PendingFolderUi
import dev.lostf1sh.syncthing.ui.folders.appendIgnorePattern
import dev.lostf1sh.syncthing.api.dto.BrowseEntry
import dev.lostf1sh.syncthing.data.ConflictResolver
import dev.lostf1sh.syncthing.data.FolderCondition
import dev.lostf1sh.syncthing.data.parseFolderConditions
import dev.lostf1sh.syncthing.data.serializeFolderConditions
import dev.lostf1sh.syncthing.ui.errors.ConflictScreen
import dev.lostf1sh.syncthing.ui.errors.ErrorCenterScreen
import dev.lostf1sh.syncthing.ui.home.HomeScreen
import dev.lostf1sh.syncthing.ui.home.InsightsScreen
import dev.lostf1sh.syncthing.ui.home.RecentChangesScreen
import dev.lostf1sh.syncthing.ui.share.ShareTargetScreen
import dev.lostf1sh.syncthing.ui.share.copyUrisToFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import dev.lostf1sh.syncthing.ui.onboarding.OnboardingScreen
import dev.lostf1sh.syncthing.ui.qr.ShowQrDialog
import dev.lostf1sh.syncthing.ui.qr.QrScannerScreen
import dev.lostf1sh.syncthing.ui.settings.BatteryWizardScreen
import dev.lostf1sh.syncthing.ui.settings.DiagnosticsScreen
import dev.lostf1sh.syncthing.ui.settings.ProfilesScreen
import dev.lostf1sh.syncthing.ui.settings.SettingsScreen
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset

@Composable
fun AppNavigation(
    pendingShortcut: androidx.compose.runtime.MutableState<PendingShortcut?>? = null,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val serviceState by SyncthingService.state.collectAsStateWithLifecycle()
    val app = LocalContext.current.applicationContext as SyncthingApp
    val container = app.container
    val appState = container.appState
    val onboardingDone by container.settingsStore.onboardingComplete
        .collectAsStateWithLifecycle(initialValue = null)

    // Process-wide state, collected in AppContainer. Tiles read the
    // same flows — UI just mirrors them.
    val folders by appState.folders.collectAsStateWithLifecycle()
    val devices by appState.devices.collectAsStateWithLifecycle()
    val folderStates by appState.folderStates.collectAsStateWithLifecycle()
    val folderStatuses by appState.folderStatuses.collectAsStateWithLifecycle()
    val deviceConnections by appState.deviceConnections.collectAsStateWithLifecycle()
    val health by appState.health.collectAsStateWithLifecycle()
    val issues by appState.issues.collectAsStateWithLifecycle()
    val localDeviceId by appState.localDeviceId.collectAsStateWithLifecycle()
    val pendingFoldersMap by appState.pendingFolders.collectAsStateWithLifecycle()
    val bandwidth by appState.bandwidthHistory.collectAsStateWithLifecycle()
    val folderStats by appState.folderStats.collectAsStateWithLifecycle()
    val deviceStats by appState.deviceStats.collectAsStateWithLifecycle()
    val recentChanges by appState.recentChanges.collectAsStateWithLifecycle()
    val pendingDevices by appState.pendingDevices.collectAsStateWithLifecycle()
    val systemStatus by appState.systemStatus.collectAsStateWithLifecycle()
    val logs by appState.logs.collectAsStateWithLifecycle()
    val diagnostic by appState.diagnostic.collectAsStateWithLifecycle()
    val folderConditionsRaw by container.settingsStore.folderConditions.collectAsStateWithLifecycle(initialValue = "{}")
    val folderConditions = remember(folderConditionsRaw) { parseFolderConditions(folderConditionsRaw) }
    var showLocalDeviceCode by remember { mutableStateOf(false) }

    if (showLocalDeviceCode) {
        ShowQrDialog(
            deviceId = localDeviceId.orEmpty(),
            onDismiss = { showLocalDeviceCode = false },
        )
    }

    // Boot the collector loop when the service is Running. Tear down only on
    // terminal states — keep caches warm during transient Paused/Starting so a
    // brief Wi-Fi blip doesn't wipe the UI state.
    LaunchedEffect(serviceState) {
        when (val s = serviceState) {
            is RunState.Running -> container.initClient(s.apiKey, s.port)
            is RunState.Stopped, is RunState.Crashed -> container.tearDown()
            else -> Unit
        }
    }

    // Consume a pending shortcut once onboarding is done.
    var incomingShareUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val shortcut = pendingShortcut?.value
    LaunchedEffect(shortcut, onboardingDone) {
        if (shortcut == null || onboardingDone != true) return@LaunchedEffect
        when (shortcut) {
            PendingShortcut.ErrorCenter -> navController.navigate(ErrorCenterRoute)
            PendingShortcut.Insights -> navController.navigate(InsightsRoute)
            is PendingShortcut.Share -> {
                incomingShareUris = shortcut.uris
                navController.navigate(ShareTargetRoute)
            }
        }
        pendingShortcut.value = null
    }

    // Surface the first pending offer as a dialog. Dismissed offers stay
    // dismissed for this session via rememberSaveable in practice — here we
    // drop anything the user has already acted on.
    var dismissedOffers by remember { mutableStateOf(emptySet<String>()) }
    val pendingFolder: PendingFolderUi? = remember(pendingFoldersMap, devices, dismissedOffers) {
        pendingFoldersMap.entries
            .firstOrNull { (folderId, _) -> folderId !in dismissedOffers }
            ?.let { (folderId, pending) ->
                val (deviceId, info) = pending.offeredBy.entries.firstOrNull() ?: return@let null
                val deviceName = devices.find { it.deviceID == deviceId }?.name ?: ""
                PendingFolderUi(
                    folderId = folderId,
                    label = info.label,
                    offeredByDevice = deviceId,
                    offeredByName = deviceName,
                )
            }
    }

    val done = onboardingDone == true

    if (done) {
        pendingFolder?.let { pf ->
            AcceptFolderDialog(
                pending = pf,
                onAccept = { path ->
                    scope.launch {
                        try {
                            container.client?.addFolder(
                                Folder(
                                    id = pf.folderId,
                                    label = pf.label,
                                    path = path,
                                    devices = listOf(FolderDevice(deviceID = pf.offeredByDevice)),
                                )
                            )
                            container.client?.dismissPendingFolder(pf.folderId, pf.offeredByDevice)
                        } catch (_: Exception) { }
                    }
                    dismissedOffers = dismissedOffers + pf.folderId
                },
                onDismiss = {
                    scope.launch {
                        try {
                            container.client?.dismissPendingFolder(pf.folderId, pf.offeredByDevice)
                        } catch (_: Exception) { }
                    }
                    dismissedOffers = dismissedOffers + pf.folderId
                },
            )
        }
    }

    val startDest: Any = if (done) HomeRoute else OnboardingRoute

    // Expressive predictive-back transitions — nav-compose drives progress
    // from the system back gesture when enableOnBackInvokedCallback=true.
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val slideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    NavHost(
        navController = navController,
        startDestination = startDest,
        enterTransition = {
            slideIntoContainer(
                towards = SlideDirection.Start,
                animationSpec = slideSpec,
            ) + fadeIn(animationSpec = spatialSpec)
        },
        exitTransition = {
            slideOutOfContainer(
                towards = SlideDirection.Start,
                animationSpec = slideSpec,
                targetOffset = { it / 6 },
            ) + fadeOut(animationSpec = spatialSpec)
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = SlideDirection.End,
                animationSpec = slideSpec,
                initialOffset = { it / 6 },
            ) + fadeIn(animationSpec = spatialSpec)
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = SlideDirection.End,
                animationSpec = slideSpec,
            ) + fadeOut(animationSpec = spatialSpec)
        },
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                settingsStore = container.settingsStore,
                onComplete = {
                    navController.navigate(HomeRoute) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                },
            )
        }
        composable<HomeRoute>(
            deepLinks = listOf(navDeepLink { uriPattern = "syncthing://home" }),
        ) {
            HomeScreen(
                folders = folders,
                folderStates = folderStates,
                folderStatuses = folderStatuses,
                devices = devices,
                deviceConnections = deviceConnections,
                onFolderClick = { navController.navigate(FolderRoute(it)) },
                onAddFolder = { navController.navigate(AddFolderRoute) },
                onDeviceClick = { navController.navigate(DeviceRoute(it)) },
                onAddDevice = { navController.navigate(AddDeviceRoute()) },
                onScanQr = { navController.navigate(QrScannerRoute) },
                onShowDeviceCode = { showLocalDeviceCode = true },
                onSettingsClick = { navController.navigate(SettingsRoute) },
                onOverviewClick = { navController.navigate(DiagnosticsRoute) },
                onRefresh = {
                    // Suspend inline so the pull-to-refresh spinner stays up
                    // until these fetches actually land. Wrapping in scope.launch
                    // would return immediately and flip the spinner off early.
                    try {
                        container.folderRepository?.folders()?.let(appState::setFolders)
                        container.deviceRepository?.devices()?.let(appState::setDevices)
                        val fMap = mutableMapOf<String, FolderStatus>()
                        appState.folders.value.forEach { f ->
                            try {
                                val s = container.folderRepository?.folderStatus(f.id)
                                if (s != null) {
                                    fMap[f.id] = s
                                    appState.updateFolderState(f.id, s.state)
                                }
                            } catch (_: Exception) { }
                        }
                        appState.setFolderStatuses(fMap)
                        container.systemRepository?.connections()?.let(appState::setConnections)
                    } catch (_: Exception) { }
                },
                health = health,
                diagnostic = diagnostic,
                localDeviceId = localDeviceId,
                bandwidth = bandwidth,
                onInsightsClick = { navController.navigate(InsightsRoute) },
                onRecentChangesClick = { navController.navigate(RecentChangesRoute) },
            )
        }
        composable<QrScannerRoute> {
            QrScannerScreen(
                onDeviceIdScanned = { deviceId ->
                    navController.navigate(AddDeviceRoute(deviceId)) {
                        popUpTo(QrScannerRoute) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ShareTargetRoute> {
            var copying by remember { mutableStateOf(false) }
            val context = LocalContext.current
            ShareTargetScreen(
                folders = folders,
                fileCount = incomingShareUris.size,
                copying = copying,
                onBack = {
                    incomingShareUris = emptyList()
                    navController.popBackStack()
                },
                onFolderSelected = { folderId ->
                    val folder = folders.find { it.id == folderId } ?: return@ShareTargetScreen
                    val path = folder.path
                    val uris = incomingShareUris
                    if (path.isBlank() || uris.isEmpty()) return@ShareTargetScreen
                    scope.launch {
                        copying = true
                        val copied = withContext(Dispatchers.IO) {
                            copyUrisToFolder(context, uris, path)
                        }
                        if (copied > 0) {
                            try { container.client?.rescanSubdir(folderId, "_incoming") }
                            catch (_: Exception) { }
                        }
                        copying = false
                        incomingShareUris = emptyList()
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<InsightsRoute>(
            deepLinks = listOf(navDeepLink { uriPattern = "syncthing://insights" }),
        ) {
            InsightsScreen(
                folders = folders,
                folderStatuses = folderStatuses,
                folderStats = folderStats,
                devices = devices,
                deviceStats = deviceStats,
                deviceConnections = deviceConnections,
                onBack = { navController.popBackStack() },
            )
        }
        composable<FolderRoute>(
            deepLinks = listOf(navDeepLink { uriPattern = "syncthing://folder/{id}" }),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<FolderRoute>()
            val folder = folders.find { it.id == route.id }

            var status by remember { mutableStateOf<FolderStatus?>(null) }

            // Detail screen wants higher-rate status polling than the global 3s
            // cadence for smooth progress; keep this local poll.
            LaunchedEffect(route.id, serviceState) {
                val running = serviceState as? RunState.Running ?: return@LaunchedEffect
                while (currentCoroutineContext().isActive) {
                    try {
                        status = container.folderRepository?.folderStatus(route.id)
                    } catch (_: Exception) { }
                    delay(2_000)
                }
            }

            val folderId = route.id
            val currentConditions = folderConditions[folderId]
            FolderDetailScreen(
                folder = folder,
                status = status,
                onBack = { navController.popBackStack() },
                onPause = { id ->
                    scope.launch {
                        try { container.client?.pauseFolder(id) } catch (_: Exception) { }
                    }
                },
                onResume = { id ->
                    scope.launch {
                        try { container.client?.resumeFolder(id) } catch (_: Exception) { }
                    }
                },
                onRescan = { id ->
                    scope.launch {
                        try { container.client?.rescanFolder(id) } catch (_: Exception) { }
                    }
                },
                onRepairIndex = { id ->
                    scope.launch {
                        val client = container.client
                        if (client == null) {
                            appState.setDiagnostic("Could not repair folder index: Syncthing service not running")
                            return@launch
                        }
                        try {
                            client.pauseFolder(id)
                            delay(500)
                            client.resetFolderIndex(id)
                            val ready = withTimeoutOrNull(60_000) {
                                while (currentCoroutineContext().isActive) {
                                    try {
                                        if (client.ping().ping == "pong") return@withTimeoutOrNull true
                                    } catch (_: Exception) { }
                                    delay(1_000)
                                }
                                false
                            } == true
                            if (ready) {
                                client.resumeFolder(id)
                                client.rescanFolder(id)
                                appState.setDiagnostic(null)
                                appState.pushLog("App: repaired folder index for $id")
                            } else {
                                appState.setDiagnostic("Folder index reset started, but Syncthing API did not come back yet")
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                            appState.setDiagnostic("Could not repair folder index: $detail")
                            appState.pushLog("App: could not repair folder index $id: $detail")
                        }
                    }
                },
                onEdit = { id -> navController.navigate(EditFolderRoute(id)) },
                onRemove = { id ->
                    scope.launch {
                        try { container.client?.deleteFolder(id) } catch (_: Exception) { return@launch }
                        navController.popBackStack()
                    }
                },
                onBrowse = { navController.navigate(FolderBrowserRoute(it)) },
                wifiOnly = currentConditions?.wifiOnly ?: false,
                chargingOnly = currentConditions?.chargingOnly ?: false,
                onConditionsChanged = { wifi, charging ->
                    scope.launch {
                        val updated = folderConditions.toMutableMap().apply {
                            put(folderId, FolderCondition(wifi, charging))
                        }
                        container.settingsStore.setFolderConditions(serializeFolderConditions(updated))
                    }
                },
            )
        }
        composable<FolderBrowserRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<FolderBrowserRoute>()
            val folder = folders.find { it.id == route.folderId }
            var entries by remember(route.folderId, route.prefix) {
                mutableStateOf<List<BrowseEntry>>(emptyList())
            }
            var pendingPaths by remember(route.folderId) {
                mutableStateOf<Set<String>>(emptySet())
            }
            var loading by remember(route.folderId, route.prefix) { mutableStateOf(true) }
            val context = LocalContext.current

            suspend fun load() {
                loading = true
                try {
                    entries = container.client?.browseFolder(
                        folderId = route.folderId,
                        prefix = route.prefix,
                        levels = 0,
                    ) ?: emptyList()
                } catch (_: Exception) { }
                try {
                    val need = container.client?.folderNeed(route.folderId)
                    val paths = buildSet {
                        need?.progress?.forEach { add(it.name) }
                        need?.queued?.forEach { add(it.name) }
                        need?.rest?.forEach { add(it.name) }
                    }
                    pendingPaths = paths
                } catch (_: Exception) { }
                loading = false
            }

            LaunchedEffect(route.folderId, route.prefix, serviceState) {
                if (serviceState is RunState.Running) load()
            }

            FolderBrowserScreen(
                folderLabel = folder?.label?.ifBlank { folder.id } ?: route.folderId,
                prefix = route.prefix,
                entries = entries,
                pendingPaths = pendingPaths,
                loading = loading,
                onBack = { navController.popBackStack() },
                onRefresh = { load() },
                onOpenDirectory = { newPrefix ->
                    navController.navigate(FolderBrowserRoute(route.folderId, newPrefix))
                },
                onRescan = { sub ->
                    scope.launch {
                        try { container.client?.rescanSubdir(route.folderId, sub) }
                        catch (_: Exception) { }
                    }
                },
                onCopyPath = { path ->
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Syncthing path", path))
                },
                onAddToIgnores = { path ->
                    scope.launch {
                        try {
                            val current = container.client?.folderIgnores(route.folderId)
                            val next = appendIgnorePattern(current?.ignore ?: emptyList(), path)
                            container.client?.setFolderIgnores(route.folderId, next)
                        } catch (_: Exception) { }
                    }
                },
                onEditIgnores = {
                    navController.navigate(IgnoreEditorRoute(route.folderId))
                },
            )
        }
        composable<IgnoreEditorRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<IgnoreEditorRoute>()
            val folder = folders.find { it.id == route.folderId }
            var patterns by remember(route.folderId) { mutableStateOf<List<String>>(emptyList()) }
            var loading by remember(route.folderId) { mutableStateOf(true) }
            LaunchedEffect(route.folderId, serviceState) {
                if (serviceState !is RunState.Running) return@LaunchedEffect
                loading = true
                try {
                    patterns = container.client?.folderIgnores(route.folderId)?.ignore ?: emptyList()
                } catch (_: Exception) { }
                loading = false
            }
            IgnoreEditorScreen(
                folderLabel = folder?.label?.ifBlank { folder.id } ?: route.folderId,
                initialPatterns = patterns,
                loading = loading,
                onBack = { navController.popBackStack() },
                onSave = { list ->
                    scope.launch {
                        try { container.client?.setFolderIgnores(route.folderId, list) }
                        catch (_: Exception) { }
                        navController.popBackStack()
                    }
                },
            )
        }
        composable<EditFolderRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditFolderRoute>()
            val folder = folders.find { it.id == route.folderId }
            if (folder == null) {
                LaunchedEffect(route.folderId) { navController.popBackStack() }
                return@composable
            }
            EditFolderScreen(
                folderId = folder.id,
                initialLabel = folder.label,
                initialPath = folder.path,
                initialType = folder.type,
                devices = devices,
                localDeviceId = localDeviceId,
                initialSharedDeviceIds = folder.devices.map { it.deviceID }.toSet(),
                onSave = { label, path, type, sharedDeviceIds ->
                    val folderRepo = container.folderRepository
                        ?: return@EditFolderScreen Result.failure(
                            Exception("Syncthing service not running")
                        )
                    val localId = localDeviceId.orEmpty()
                    if (localId.isBlank()) {
                        return@EditFolderScreen Result.failure(Exception("Local device ID unavailable"))
                    }
                    val deviceIds = (sharedDeviceIds + localId).toList()
                    try {
                        val updated = folder.copy(
                            label = label,
                            path = path,
                            type = type,
                            devices = deviceIds.map { FolderDevice(deviceID = it) },
                        )
                        folderRepo.updateFolder(updated)
                        appState.setFolders(folderRepo.folders())
                        appState.setDiagnostic(null)
                        appState.pushLog("App: folder updated ${folder.id}")
                        Result.success(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        appState.setDiagnostic("Could not update folder: $detail")
                        appState.pushLog("App: could not update folder ${folder.id}: $detail")
                        Result.failure(e)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<EditDeviceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<EditDeviceRoute>()
            val device = devices.find { it.deviceID == route.deviceId }
            if (device == null) {
                LaunchedEffect(route.deviceId) { navController.popBackStack() }
                return@composable
            }
            EditDeviceScreen(
                deviceId = device.deviceID,
                initialName = device.name,
                initialAddresses = device.addresses,
                initialCompression = device.compression,
                initialIntroducer = device.introducer,
                initialAutoAcceptFolders = device.autoAcceptFolders,
                onSave = { name, addresses, compression, introducer, autoAcceptFolders ->
                    val deviceRepo = container.deviceRepository
                        ?: return@EditDeviceScreen Result.failure(
                            Exception("Syncthing service not running")
                        )
                    try {
                        val updated = device.copy(
                            name = name,
                            addresses = addresses,
                            compression = compression,
                            introducer = introducer,
                            autoAcceptFolders = autoAcceptFolders,
                        )
                        deviceRepo.updateDevice(updated)
                        appState.setDevices(deviceRepo.devices())
                        appState.setDiagnostic(null)
                        appState.pushLog("App: device updated ${device.deviceID}")
                        Result.success(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        appState.setDiagnostic("Could not update device: $detail")
                        appState.pushLog("App: could not update device ${device.deviceID}: $detail")
                        Result.failure(e)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<DeviceRoute>(
            deepLinks = listOf(navDeepLink { uriPattern = "syncthing://device/{id}" }),
        ) { backStackEntry ->
            val route = backStackEntry.toRoute<DeviceRoute>()
            val device = devices.find { it.deviceID == route.id }

            var connection by remember { mutableStateOf<ConnectionInfo?>(null) }
            LaunchedEffect(route.id, serviceState) {
                val running = serviceState as? RunState.Running ?: return@LaunchedEffect
                try {
                    connection = container.systemRepository?.connections()
                        ?.connections?.get(route.id)
                } catch (_: Exception) { }
                container.eventRepository?.deviceConnections()?.collect { (id, _) ->
                    if (id == route.id) {
                        try {
                            connection = container.systemRepository?.connections()
                                ?.connections?.get(route.id)
                        } catch (_: Exception) { }
                    }
                }
            }

            DeviceDetailScreen(
                device = device,
                connection = connection,
                pendingDevices = pendingDevices,
                onBack = { navController.popBackStack() },
                onPause = { id ->
                    scope.launch {
                        try { container.client?.pauseDevice(id) } catch (_: Exception) { }
                    }
                },
                onResume = { id ->
                    scope.launch {
                        try { container.client?.resumeDevice(id) } catch (_: Exception) { }
                    }
                },
                onEdit = { id -> navController.navigate(EditDeviceRoute(id)) },
                onShareExistingFolders = { deviceId ->
                    scope.launch {
                        val folderRepo = container.folderRepository
                        if (folderRepo == null) {
                            appState.setDiagnostic("Could not share folders: Syncthing service not running")
                            appState.pushLog("App: could not share folders with $deviceId: service not running")
                            return@launch
                        }
                        try {
                            folders.forEach { folder ->
                                if (folder.devices.none { it.deviceID == deviceId }) {
                                    folderRepo.setFolderDevices(
                                        folderId = folder.id,
                                        devices = folder.devices + FolderDevice(deviceID = deviceId),
                                    )
                                }
                            }
                            appState.setFolders(folderRepo.folders())
                            appState.setDiagnostic(null)
                            appState.pushLog("App: existing folders shared with $deviceId")
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                            appState.setDiagnostic("Could not share folders with device: $detail")
                            appState.pushLog("App: could not share folders with $deviceId: $detail")
                        }
                    }
                },
                onRemove = { id ->
                    scope.launch {
                        try { container.client?.deleteDevice(id) } catch (_: Exception) { return@launch }
                        navController.popBackStack()
                    }
                },
                localDeviceId = localDeviceId,
            )
        }
        composable<AddDeviceRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<AddDeviceRoute>()
            AddDeviceScreen(
                initialDeviceId = route.prefillId,
                onAdd = { deviceId, name, shareExistingFolders ->
                    val repo = container.deviceRepository
                        ?: return@AddDeviceScreen Result.failure(
                            Exception("Syncthing service not running")
                        )
                    val folderRepo = container.folderRepository
                        ?: return@AddDeviceScreen Result.failure(
                            Exception("Syncthing service not running")
                        )
                    try {
                        if (devices.none { it.deviceID == deviceId }) {
                            repo.addDevice(
                                DeviceDto(
                                    deviceID = deviceId,
                                    name = name,
                                    addresses = listOf("dynamic"),
                                )
                            )
                        }
                        if (shareExistingFolders) {
                            folders.forEach { folder ->
                                if (folder.devices.none { it.deviceID == deviceId }) {
                                    folderRepo.setFolderDevices(
                                        folderId = folder.id,
                                        devices = folder.devices + FolderDevice(deviceID = deviceId),
                                    )
                                }
                            }
                        }
                        appState.setFolders(folderRepo.folders())
                        appState.setDevices(repo.devices())
                        appState.setDiagnostic(null)
                        appState.pushLog("App: device added and existing folders shared with $deviceId")
                        Result.success(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        appState.setDiagnostic("Could not add or share device: $detail")
                        appState.pushLog("App: could not add or share device $deviceId: $detail")
                        Result.failure(e)
                    }
                },
                onScanQr = { navController.navigate(QrScannerRoute) },
                onBack = { navController.popBackStack() },
            )
        }
        composable<AddFolderRoute> {
            AddFolderScreen(
                onAdd = { folderId, label, path, type ->
                    val folderRepo = container.folderRepository
                        ?: return@AddFolderScreen Result.failure(
                            Exception("Syncthing service not running")
                        )
                    val system = appState.systemStatus.value
                    val localId = system?.myID.orEmpty()
                    if (localId.isBlank()) {
                        return@AddFolderScreen Result.failure(Exception("Local device ID unavailable"))
                    }
                    try {
                        if (folders.any { it.id.equals(folderId, ignoreCase = true) }) {
                            return@AddFolderScreen Result.failure(Exception("Folder ID already exists"))
                        }
                        folderRepo.addFolder(
                            Folder(
                                id = folderId,
                                label = label,
                                path = path,
                                type = type,
                                devices = listOf(FolderDevice(deviceID = localId)),
                            )
                        )
                        appState.setFolders(folderRepo.folders())
                        appState.setDiagnostic(null)
                        appState.pushLog("App: folder added $folderId")
                        Result.success(Unit)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val detail = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName
                        appState.setDiagnostic("Could not add folder: $detail")
                        appState.pushLog("App: could not add folder $folderId: $detail")
                        Result.failure(e)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable<ErrorCenterRoute>(
            deepLinks = listOf(navDeepLink { uriPattern = "syncthing://errors" }),
        ) {
            ErrorCenterScreen(
                issues = issues,
                onBack = { navController.popBackStack() },
                onRescan = { id ->
                    scope.launch {
                        try { container.client?.rescanFolder(id) } catch (_: Exception) { }
                    }
                },
            )
        }
        composable<ConflictRoute>(
            deepLinks = listOf(navDeepLink { uriPattern = "syncthing://conflicts" }),
        ) {
            val conflicts by appState.conflicts.collectAsStateWithLifecycle()
            val foldersNow = folders
            fun pathOf(folderId: String): String? =
                foldersNow.find { it.id == folderId }?.path?.ifBlank { null }

            ConflictScreen(
                conflicts = conflicts,
                onBack = { navController.popBackStack() },
                onKeepLocal = { c ->
                    val folderPath = pathOf(c.folderId) ?: return@ConflictScreen
                    scope.launch {
                        ConflictResolver.keepCurrent(folderPath, c.path)
                        try { container.client?.rescanFolder(c.folderId) } catch (_: Exception) { }
                    }
                },
                onKeepRemote = { c ->
                    val folderPath = pathOf(c.folderId) ?: return@ConflictScreen
                    scope.launch {
                        ConflictResolver.keepConflict(folderPath, c.path)
                        try { container.client?.rescanFolder(c.folderId) } catch (_: Exception) { }
                    }
                },
                onDuplicate = { c ->
                    // "Keep both" is filesystem-no-op (both files already
                    // exist), but users need feedback that their tap
                    // registered. Fire a rescan so the list refreshes and the
                    // tap feels responsive.
                    scope.launch {
                        try { container.client?.rescanFolder(c.folderId) }
                        catch (_: Exception) { }
                    }
                },
            )
        }
        composable<ProfilesRoute> {
            ProfilesScreen(
                settingsStore = container.settingsStore,
                onBack = { navController.popBackStack() },
            )
        }
        composable<DiagnosticsRoute> {
            DiagnosticsScreen(
                settingsStore = container.settingsStore,
                systemStatus = systemStatus,
                logs = logs,
                onBack = { navController.popBackStack() },
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                settingsStore = container.settingsStore,
                onBack = { navController.popBackStack() },
                onProfilesClick = { navController.navigate(ProfilesRoute) },
                onDiagnosticsClick = { navController.navigate(DiagnosticsRoute) },
                onErrorCenterClick = { navController.navigate(ErrorCenterRoute) },
                onBatteryWizardClick = { navController.navigate(BatteryWizardRoute) },
            )
        }
        composable<RecentChangesRoute> {
            val folderLabels = remember(folders) {
                folders.associate { it.id to it.label.ifBlank { it.id } }
            }
            RecentChangesScreen(
                changes = recentChanges,
                folderLabels = folderLabels,
                onBack = { navController.popBackStack() },
            )
        }
        composable<BatteryWizardRoute> {
            BatteryWizardScreen(onBack = { navController.popBackStack() })
        }
    }
}

package dev.lostf1sh.syncthing.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
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
import dev.lostf1sh.syncthing.ui.folders.AcceptFolderDialog
import dev.lostf1sh.syncthing.ui.folders.FolderBrowserScreen
import dev.lostf1sh.syncthing.ui.folders.FolderDetailScreen
import dev.lostf1sh.syncthing.ui.folders.IgnoreEditorScreen
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
import kotlinx.coroutines.withContext
import dev.lostf1sh.syncthing.ui.onboarding.OnboardingScreen
import dev.lostf1sh.syncthing.ui.settings.BatteryWizardScreen
import dev.lostf1sh.syncthing.ui.settings.DiagnosticsScreen
import dev.lostf1sh.syncthing.ui.settings.ProfilesScreen
import dev.lostf1sh.syncthing.ui.settings.SettingsScreen
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun AppNavigation(
    pendingShortcut: androidx.compose.runtime.MutableState<PendingShortcut?>? = null,
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val serviceState by SyncthingService.state.collectAsStateWithLifecycle()
    val app = navController.context.applicationContext as SyncthingApp
    val container = app.container
    val appState = container.appState
    val onboardingDone by container.settingsStore.onboardingComplete
        .collectAsStateWithLifecycle(initialValue = null)

    // Process-wide state, collected in AppContainer. Widgets & tiles read the
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
    val folderConditionsRaw by container.settingsStore.folderConditions.collectAsStateWithLifecycle(initialValue = "{}")
    val folderConditions = remember(folderConditionsRaw) { parseFolderConditions(folderConditionsRaw) }

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
                onDeviceClick = { navController.navigate(DeviceRoute(it)) },
                onAddDevice = { navController.navigate(AddDeviceRoute()) },
                onScanQr = { /* ML Kit scanner launch */ },
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
                localDeviceId = localDeviceId,
                bandwidth = bandwidth,
                onInsightsClick = { navController.navigate(InsightsRoute) },
                onRecentChangesClick = { navController.navigate(RecentChangesRoute) },
            )
        }
        composable<ShareTargetRoute> {
            var copying by remember { mutableStateOf(false) }
            val context = navController.context
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
            val context = navController.context

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
                onAdd = { deviceId, name ->
                    val repo = container.deviceRepository
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
                        Result.success(Unit)
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                },
                onScanQr = { /* ML Kit scanner */ },
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

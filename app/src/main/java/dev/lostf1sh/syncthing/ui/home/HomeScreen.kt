package dev.lostf1sh.syncthing.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.lostf1sh.syncthing.R
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import dev.lostf1sh.syncthing.data.model.BandwidthSample
import dev.lostf1sh.syncthing.data.model.SyncHealth
import dev.lostf1sh.syncthing.service.SyncthingService
import dev.lostf1sh.syncthing.ui.core.displayColor
import dev.lostf1sh.syncthing.ui.core.displayLabelWithReason
import dev.lostf1sh.syncthing.ui.devices.DevicesScreen
import dev.lostf1sh.syncthing.ui.folders.FoldersScreen
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    folders: List<Folder>,
    folderStates: Map<String, String>,
    folderStatuses: Map<String, FolderStatus> = emptyMap(),
    devices: List<Device>,
    deviceConnections: Map<String, Boolean>,
    onFolderClick: (String) -> Unit,
    onAddFolder: () -> Unit,
    onDeviceClick: (String) -> Unit,
    onAddDevice: () -> Unit,
    onScanQr: () -> Unit,
    onShowDeviceCode: () -> Unit = {},
    onSettingsClick: () -> Unit,
    onOverviewClick: () -> Unit = {},
    onRefresh: (suspend () -> Unit)? = null,
    health: SyncHealth? = null,
    diagnostic: String? = null,
    localDeviceId: String? = null,
    bandwidth: List<BandwidthSample> = emptyList(),
    onInsightsClick: () -> Unit = {},
    onRecentChangesClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by SyncthingService.state.collectAsStateWithLifecycle()
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            // Expressive: LargeFlexibleTopAppBar — big hero that collapses on scroll
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = {
                    Text(
                        text = state.displayLabelWithReason(),
                        color = state.displayColor(),
                    )
                },
                actions = {
                    IconButton(
                        onClick = onShowDeviceCode,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = stringResource(R.string.cd_show_device_code))
                    }
                    // Expressive: IconButton with animated shapes
                    IconButton(
                        onClick = onSettingsClick,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (!diagnostic.isNullOrBlank()) {
                DiagnosticBanner(
                    message = diagnostic,
                    onClick = onOverviewClick,
                )
            }
            ExpressiveHomeTabs(
                selectedPage = pagerState.currentPage,
                onPageSelected = { page ->
                    scope.launch { pagerState.animateScrollToPage(page) }
                },
            )
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> OverviewTab(
                        health = health,
                        bandwidth = bandwidth,
                        onOverviewClick = onOverviewClick,
                        onInsightsClick = onInsightsClick,
                        onRecentChangesClick = onRecentChangesClick,
                    )
                    1 -> FoldersScreen(
                        folders = folders,
                        folderStates = folderStates,
                        folderStatuses = folderStatuses,
                        onFolderClick = onFolderClick,
                        onAddFolder = onAddFolder,
                        onRefresh = onRefresh,
                    )
                    2 -> DevicesScreen(
                        devices = devices,
                        connections = deviceConnections,
                        onDeviceClick = onDeviceClick,
                        onAddDevice = onAddDevice,
                        onScanQr = onScanQr,
                        onRefresh = onRefresh,
                        localDeviceId = localDeviceId,
                    )
                }
            }
        }
    }
}

private data class HomeTabItem(
    val label: String,
    val icon: ImageVector,
)

@Composable
private fun ExpressiveHomeTabs(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        HomeTabItem(stringResource(R.string.tab_overview), Icons.Default.Insights),
        HomeTabItem(stringResource(R.string.tab_folders), Icons.Default.Folder),
        HomeTabItem(stringResource(R.string.tab_devices), Icons.Default.Devices),
    )
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            tabs.forEachIndexed { index, tab ->
                ExpressiveHomeTab(
                    index = index,
                    tab = tab,
                    selectedPage = selectedPage,
                    selected = selectedPage == index,
                    onClick = { onPageSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ExpressiveHomeTab(
    index: Int,
    tab: HomeTabItem,
    selectedPage: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    var hasAnimatedSelectionChange by remember { mutableStateOf(false) }
    val motionSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val iconMotionSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()

    LaunchedEffect(selectedPage) {
        if (!hasAnimatedSelectionChange) {
            hasAnimatedSelectionChange = true
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            return@LaunchedEffect
        }
        if (selected) {
            launch {
                scale.animateTo(1.05f, animationSpec = motionSpec)
                scale.animateTo(1f, animationSpec = motionSpec)
            }
            offsetX.snapTo(0f)
        } else {
            scale.snapTo(1f)
            val distance = index - selectedPage
            if (abs(distance) == 1) {
                val direction = if (distance > 0) 1f else -1f
                launch {
                    offsetX.animateTo(10f * direction, animationSpec = motionSpec)
                    offsetX.animateTo(0f, animationSpec = motionSpec)
                }
            } else {
                offsetX.snapTo(0f)
            }
        }
    }

    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tabContainerColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tabContentColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tabBorderColor",
    )
    val iconSize by animateDpAsState(
        targetValue = if (selected) 22.dp else 20.dp,
        animationSpec = iconMotionSpec,
        label = "tabIconSize",
    )

    Row(
        modifier = modifier
            .padding(5.dp)
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offsetX.value
            }
            .clip(shape)
            .background(containerColor)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = tab.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OverviewTab(
    health: SyncHealth?,
    bandwidth: List<BandwidthSample>,
    onOverviewClick: () -> Unit,
    onInsightsClick: () -> Unit,
    onRecentChangesClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
    ) {
        if (health != null) {
            SyncOverviewCard(
                health = health,
                onClick = onOverviewClick,
            )
            InsightsCard(
                bandwidth = bandwidth,
                onClick = onInsightsClick,
            )
            RecentChangesCard(onClick = onRecentChangesClick)
            HealthBanner(health = health)
        } else {
            Text(
                text = stringResource(R.string.starting_syncthing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun DiagnosticBanner(
    message: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.syncthing_problem),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

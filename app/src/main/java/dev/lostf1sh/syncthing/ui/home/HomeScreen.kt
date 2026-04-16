package dev.lostf1sh.syncthing.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.R
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.native.RunState
import dev.lostf1sh.syncthing.service.SyncthingService
import dev.lostf1sh.syncthing.ui.devices.DevicesScreen
import dev.lostf1sh.syncthing.ui.folders.FoldersScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeScreen(
    folders: List<Folder>,
    folderStates: Map<String, String>,
    devices: List<Device>,
    deviceConnections: Map<String, Boolean>,
    onFolderClick: (String) -> Unit,
    onDeviceClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by SyncthingService.state.collectAsState()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        Text(
                            text = stateLabel(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = stateColor(state),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onSettingsClick,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Folders") },
                    icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Devices") },
                    icon = { Icon(Icons.Default.Devices, contentDescription = null) },
                )
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                when (page) {
                    0 -> FoldersScreen(
                        folders = folders,
                        folderStates = folderStates,
                        onFolderClick = onFolderClick,
                    )
                    1 -> DevicesScreen(
                        devices = devices,
                        connections = deviceConnections,
                        onDeviceClick = onDeviceClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun stateColor(state: RunState) = when (state) {
    is RunState.Running -> MaterialTheme.colorScheme.primary
    is RunState.Crashed -> MaterialTheme.colorScheme.error
    is RunState.Paused -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun stateLabel(state: RunState): String = when (state) {
    is RunState.Stopped -> "Stopped"
    is RunState.Starting -> "Starting..."
    is RunState.Running -> "Running"
    is RunState.Crashed -> "Crashed"
    is RunState.Paused -> "Paused"
}

package dev.lostf1sh.syncthing.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.api.dto.DeviceStats
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStats
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InsightsScreen(
    folders: List<Folder>,
    folderStatuses: Map<String, FolderStatus>,
    folderStats: Map<String, FolderStats>,
    devices: List<Device>,
    deviceStats: Map<String, DeviceStats>,
    deviceConnections: Map<String, Boolean>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                indicator = { tabPositions ->
                    InsightsTabIndicator(
                        tabPositions = tabPositions,
                        selectedTab = pagerState.currentPage,
                    )
                },
            ) {
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
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (page) {
                    0 -> FolderInsightList(folders, folderStatuses, folderStats)
                    1 -> DeviceInsightList(devices, deviceStats, deviceConnections)
                }
            }
        }
    }
}

@Composable
private fun InsightsTabIndicator(
    tabPositions: List<TabPosition>,
    selectedTab: Int,
) {
    TabRowDefaults.SecondaryIndicator(
        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab])
    )
}

@Composable
private fun FolderInsightList(
    folders: List<Folder>,
    statuses: Map<String, FolderStatus>,
    stats: Map<String, FolderStats>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(folders, key = { it.id }) { folder ->
            val status = statuses[folder.id]
            val stat = stats[folder.id]
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        folder.label.ifBlank { folder.id },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    val global = status?.let {
                        "${it.globalFiles} files · ${formatBytes(it.globalBytes)}"
                    } ?: "—"
                    StatRow("Global", global)
                    StatRow("Local", status?.let {
                        "${it.localFiles} files · ${formatBytes(it.localBytes)}"
                    } ?: "—")
                    StatRow("Last file", stat?.lastFile?.filename?.takeIf { it.isNotBlank() } ?: "—")
                    StatRow("Last scan", stat?.lastScan?.take(19)?.replace("T", " ") ?: "—")
                }
            }
        }
    }
}

@Composable
private fun DeviceInsightList(
    devices: List<Device>,
    stats: Map<String, DeviceStats>,
    connections: Map<String, Boolean>,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(devices, key = { it.deviceID }) { device ->
            val stat = stats[device.deviceID]
            val connected = connections[device.deviceID] == true
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        device.name.ifBlank { device.deviceID.take(7) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    StatRow("State", if (connected) "Connected" else "Disconnected")
                    StatRow("Last seen", stat?.lastSeen?.take(19)?.replace("T", " ") ?: "—")
                    StatRow("Device ID", device.deviceID.take(16) + "…")
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KiB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MiB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GiB".format(gb)
}

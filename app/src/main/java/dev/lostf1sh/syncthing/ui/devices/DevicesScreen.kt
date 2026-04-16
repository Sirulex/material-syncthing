package dev.lostf1sh.syncthing.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.ui.core.components.EmptyState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevicesScreen(
    devices: List<Device>,
    connections: Map<String, Boolean>,
    onDeviceClick: (String) -> Unit,
    onAddDevice: (() -> Unit)? = null,
    onScanQr: (() -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (devices.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            EmptyState(
                title = "No devices",
                description = "Add a remote device to start syncing.",
                actionLabel = "Add Device",
                onAction = onAddDevice,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                val action = onRefresh ?: return@PullToRefreshBox
                scope.launch {
                    refreshing = true
                    try { action() } finally { refreshing = false }
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(devices, key = { it.deviceID }) { device ->
                    DeviceCard(
                        device = device,
                        isConnected = connections[device.deviceID] == true,
                        onClick = { onDeviceClick(device.deviceID) },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Expressive FAB Menu — expandable with QR scan + manual add
        FloatingActionButtonMenu(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            expanded = fabExpanded,
            button = {
                ToggleFloatingActionButton(
                    checked = fabExpanded,
                    onCheckedChange = { fabExpanded = !fabExpanded },
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add device",
                    )
                }
            },
        ) {
            FloatingActionButtonMenuItem(
                onClick = {
                    fabExpanded = false
                    onScanQr?.invoke()
                },
                icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                text = { Text("Scan QR Code") },
            )
            FloatingActionButtonMenuItem(
                onClick = {
                    fabExpanded = false
                    onAddDevice?.invoke()
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Enter Device ID") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceCard(
    device: Device,
    isConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Decorative connection indicator — NOT an interactive button.
            // The whole card already handles clicks; a nested IconButton confused
            // TalkBack by exposing two click targets with the same action.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isConnected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = MaterialTheme.shapes.large,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Devices,
                    contentDescription = null,
                    tint = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name.ifBlank { device.deviceID.take(7) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (isConnected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Connection dot
            if (isConnected) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(8.dp)
                ) {
                    drawCircle(color = androidx.compose.ui.graphics.Color(0xFF059669))
                }
            }
        }
    }
}

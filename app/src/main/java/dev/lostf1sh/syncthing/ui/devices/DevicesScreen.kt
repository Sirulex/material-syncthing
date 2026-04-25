package dev.lostf1sh.syncthing.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.lostf1sh.syncthing.R
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.ui.core.components.EmptyState
import dev.lostf1sh.syncthing.ui.core.theme.StatusTokens

private enum class DeviceFilter { All, Online, Offline }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DevicesScreen(
    devices: List<Device>,
    connections: Map<String, Boolean>,
    onDeviceClick: (String) -> Unit,
    onAddDevice: (() -> Unit)? = null,
    onScanQr: (() -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    localDeviceId: String? = null,
    onTogglePause: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var selectedFilter by rememberSaveable { mutableStateOf(DeviceFilter.All) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    val filteredDevices = remember(devices, connections, selectedFilter) {
        devices.filter { device ->
            when (selectedFilter) {
                DeviceFilter.All -> true
                DeviceFilter.Online -> connections[device.deviceID] == true
                DeviceFilter.Offline -> connections[device.deviceID] != true
            }
        }
    }

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
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedFilter == DeviceFilter.All,
                            onClick = { selectedFilter = DeviceFilter.All },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        FilterChip(
                            selected = selectedFilter == DeviceFilter.Online,
                            onClick = { selectedFilter = DeviceFilter.Online },
                            label = { Text(stringResource(R.string.filter_online)) },
                        )
                        FilterChip(
                            selected = selectedFilter == DeviceFilter.Offline,
                            onClick = { selectedFilter = DeviceFilter.Offline },
                            label = { Text(stringResource(R.string.filter_offline)) },
                        )
                    }
                }
                if (refreshing && devices.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }
                } else if (filteredDevices.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (devices.isEmpty()) stringResource(R.string.no_devices) else stringResource(R.string.no_matching_devices),
                            description = if (devices.isEmpty()) {
                                stringResource(R.string.no_devices_description)
                            } else {
                                stringResource(R.string.no_matching_devices_description)
                            },
                            actionLabel = stringResource(R.string.add_device),
                            onAction = onAddDevice,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                        )
                    }
                }
                items(filteredDevices, key = { it.deviceID }) { device ->
                    DeviceCard(
                        device = device,
                        isConnected = connections[device.deviceID] == true,
                        isLocal = device.deviceID == localDeviceId,
                        onClick = { onDeviceClick(device.deviceID) },
                        onTogglePause = onTogglePause?.let { toggle ->
                            { paused -> toggle(device.deviceID, paused) }
                        },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onScanQr != null) {
                SmallExtendedFloatingActionButton(
                    onClick = onScanQr,
                    icon = { Icon(Icons.Default.QrCode, contentDescription = stringResource(R.string.cd_scan_qr)) },
                    text = { Text(stringResource(R.string.scan_qr)) },
                )
            }
            if (onAddDevice != null) {
                MediumExtendedFloatingActionButton(
                    onClick = onAddDevice,
                    icon = { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_device)) },
                    text = { Text(stringResource(R.string.add_device)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeviceCard(
    device: Device,
    isConnected: Boolean,
    isLocal: Boolean,
    onClick: () -> Unit,
    onTogglePause: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
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
            val haptic = LocalHapticFeedback.current
            if (onTogglePause != null && !isLocal) {
                androidx.compose.material3.FilledIconToggleButton(
                    checked = !device.paused,
                    onCheckedChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onTogglePause(!it)
                    },
                    shapes = androidx.compose.material3.IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(
                        if (device.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                    )
                }
            } else {
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
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLocal) stringResource(R.string.your_device)
                    else device.name.ifBlank { device.deviceID.take(7) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        isLocal -> device.name.ifBlank { device.deviceID.take(7) }
                        isConnected -> stringResource(R.string.connected)
                        else -> stringResource(R.string.disconnected)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected || isLocal) StatusTokens.online
                    else StatusTokens.offline,
                )
            }

            // Connection dot (suppress for the local device — it's always "present")
            if (isConnected && !isLocal) {
                val onlineColor = StatusTokens.online
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(8.dp)
                ) {
                    drawCircle(color = onlineColor)
                }
            }
        }
    }
}

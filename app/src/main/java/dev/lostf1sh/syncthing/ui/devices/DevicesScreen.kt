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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.ui.core.components.EmptyState
import dev.lostf1sh.syncthing.ui.qr.ShowQrDialog

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
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var selectedFilter by rememberSaveable { mutableStateOf(DeviceFilter.All) }
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var showingLocalQr by rememberSaveable { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    if (showingLocalQr && !localDeviceId.isNullOrBlank()) {
        ShowQrDialog(
            deviceId = localDeviceId,
            onDismiss = { showingLocalQr = false },
        )
    }

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
                            label = { Text("All") },
                        )
                        FilterChip(
                            selected = selectedFilter == DeviceFilter.Online,
                            onClick = { selectedFilter = DeviceFilter.Online },
                            label = { Text("Online") },
                        )
                        FilterChip(
                            selected = selectedFilter == DeviceFilter.Offline,
                            onClick = { selectedFilter = DeviceFilter.Offline },
                            label = { Text("Offline") },
                        )
                    }
                }
                if (!localDeviceId.isNullOrBlank()) {
                    item {
                        LocalDeviceIdCard(
                            deviceId = localDeviceId,
                            onCopy = {
                                clipboard.setText(AnnotatedString(localDeviceId))
                            },
                            onShowQr = { showingLocalQr = true },
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
                            title = if (devices.isEmpty()) "No remote devices" else "No matching devices",
                            description = if (devices.isEmpty()) {
                                "Add your phone's device ID in the Syncthing Web GUI on your PC, then add the PC here."
                            } else {
                                "Try a different filter to view other devices."
                            },
                            actionLabel = "Add Device",
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
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    text = { Text("Scan") },
                )
            }
            MediumExtendedFloatingActionButton(
                onClick = { onAddDevice?.invoke() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Device") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalDeviceIdCard(
    deviceId: String,
    onCopy: () -> Unit,
    onShowQr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QrCode,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "This device code",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Use this in the Syncthing Web GUI on your PC.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }

            Text(
                text = deviceId,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onShowQr,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("QR Code")
                }
                FilledTonalButton(
                    onClick = onCopy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Copy ID")
                }
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
                    text = if (isLocal) "Your Device"
                    else device.name.ifBlank { device.deviceID.take(7) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = when {
                        isLocal -> device.name.ifBlank { device.deviceID.take(7) }
                        isConnected -> "Connected"
                        else -> "Disconnected"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isConnected || isLocal) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Connection dot (suppress for the local device — it's always "present")
            if (isConnected && !isLocal) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.size(8.dp)
                ) {
                    drawCircle(color = androidx.compose.ui.graphics.Color(0xFF059669))
                }
            }
        }
    }
}

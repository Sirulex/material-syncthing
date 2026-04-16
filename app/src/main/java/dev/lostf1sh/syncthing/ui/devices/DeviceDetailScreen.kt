package dev.lostf1sh.syncthing.ui.devices

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.api.dto.ConnectionInfo
import dev.lostf1sh.syncthing.api.dto.Device
import dev.lostf1sh.syncthing.ui.core.components.DetailSkeleton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DeviceDetailScreen(
    device: Device?,
    connection: ConnectionInfo?,
    onBack: () -> Unit,
    onPause: ((String) -> Unit)? = null,
    onResume: ((String) -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            // Expressive: MediumFlexibleTopAppBar
            MediumFlexibleTopAppBar(
                title = { Text(device?.name?.ifBlank { device.deviceID.take(7) } ?: "Device") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        if (device == null) {
            // Expressive: shimmer skeleton matches the detail layout
            DetailSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                rowCount = 5,
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // Expressive connected action cluster — tight 4dp spacing, animated shape morph
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ToggleButton(
                    checked = !device.paused,
                    onCheckedChange = { running ->
                        if (running) onResume?.invoke(device.deviceID)
                        else onPause?.invoke(device.deviceID)
                    },
                    shapes = ToggleButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (device.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (device.paused) "Resume" else "Pause")
                }

                OutlinedButton(
                    onClick = { clipboard.setText(AnnotatedString(device.deviceID)) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Copy ID")
                }
            }

            Spacer(Modifier.height(16.dp))

            ListItem(
                headlineContent = { Text("Device ID") },
                supportingContent = {
                    Text(
                        text = device.deviceID,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                    )
                },
            )
            ListItem(
                headlineContent = { Text("Name") },
                supportingContent = { Text(device.name.ifBlank { "(unnamed)" }) },
            )
            ListItem(
                headlineContent = { Text("Addresses") },
                supportingContent = { Text(device.addresses.joinToString(", ")) },
            )
            ListItem(
                headlineContent = { Text("Compression") },
                supportingContent = { Text(device.compression) },
            )
            ListItem(
                headlineContent = { Text("Introducer") },
                supportingContent = { Text(if (device.introducer) "Yes" else "No") },
            )

            if (connection != null && connection.connected) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Connection",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                ListItem(
                    headlineContent = { Text("Address") },
                    supportingContent = { Text(connection.address) },
                )
                ListItem(
                    headlineContent = { Text("Type") },
                    supportingContent = { Text(connection.type) },
                )
                ListItem(
                    headlineContent = { Text("Client Version") },
                    supportingContent = { Text(connection.clientVersion) },
                )
                ListItem(
                    headlineContent = { Text("Downloaded") },
                    supportingContent = { Text(formatBytes(connection.inBytesTotal)) },
                )
                ListItem(
                    headlineContent = { Text("Uploaded") },
                    supportingContent = { Text(formatBytes(connection.outBytesTotal)) },
                )
            }

            if (onRemove != null) {
                Spacer(Modifier.height(24.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Icon(Icons.Default.Delete, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Remove Device")
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showDeleteDialog && device != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove device?") },
            text = { Text("Stop syncing with \"${device.name.ifBlank { device.deviceID.take(7) }}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onRemove?.invoke(device.deviceID)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
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

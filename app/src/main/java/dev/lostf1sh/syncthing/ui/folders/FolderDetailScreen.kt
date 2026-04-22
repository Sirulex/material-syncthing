package dev.lostf1sh.syncthing.ui.folders

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import dev.lostf1sh.syncthing.ui.core.components.DetailSkeleton
import dev.lostf1sh.syncthing.ui.core.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderDetailScreen(
    folder: Folder?,
    status: FolderStatus?,
    onBack: () -> Unit,
    onPause: ((String) -> Unit)? = null,
    onResume: ((String) -> Unit)? = null,
    onRescan: ((String) -> Unit)? = null,
    onRepairIndex: ((String) -> Unit)? = null,
    onRemove: ((String) -> Unit)? = null,
    onBrowse: ((String) -> Unit)? = null,
    wifiOnly: Boolean = false,
    chargingOnly: Boolean = false,
    onConditionsChanged: ((Boolean, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRepairDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(folder?.label?.ifBlank { folder.id } ?: "Folder") },
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
        if (folder == null || status == null) {
            DetailSkeleton(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                rowCount = 6,
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

            // Status chip row
            StatusChip(state = status.state)

            Spacer(Modifier.height(12.dp))

            // Expressive connected action cluster — tight 4dp spacing, animated shape morph
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                FilledTonalButton(
                    onClick = { onRescan?.invoke(folder.id) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Rescan")
                }
                ToggleButton(
                    checked = !folder.paused,
                    onCheckedChange = { running ->
                        if (running) onResume?.invoke(folder.id)
                        else onPause?.invoke(folder.id)
                    },
                    shapes = ToggleButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        if (folder.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        null, Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(if (folder.paused) "Resume" else "Pause")
                }
            }

            // Sync progress
            if (status.state == "syncing" && status.globalBytes > 0) {
                Spacer(Modifier.height(12.dp))
                val progress = status.inSyncBytes.toFloat() / status.globalBytes.toFloat()
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(progress * 100).toInt()}% — ${formatBytes(status.inSyncBytes)} / ${formatBytes(status.globalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (status.state == "syncing") {
                Spacer(Modifier.height(12.dp))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(16.dp))

            if (onBrowse != null) {
                FilledTonalButton(
                    onClick = { onBrowse(folder.id) },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Browse files")
                }
                Spacer(Modifier.height(16.dp))
            }

            if (onRepairIndex != null) {
                OutlinedButton(
                    onClick = { showRepairDialog = true },
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Build, null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text("Repair index")
                }
                Spacer(Modifier.height(16.dp))
            }

            ListItem(
                headlineContent = { Text("Folder ID") },
                supportingContent = { Text(folder.id) },
            )
            ListItem(
                headlineContent = { Text("Path") },
                supportingContent = { Text(folder.path) },
            )
            ListItem(
                headlineContent = { Text("Type") },
                supportingContent = { Text(folderTypeLabel(folder.type)) },
            )
            ListItem(
                headlineContent = { Text("Global") },
                supportingContent = {
                    Text("${status.globalFiles} files, ${formatBytes(status.globalBytes)}")
                },
            )
            ListItem(
                headlineContent = { Text("Local") },
                supportingContent = {
                    Text("${status.localFiles} files, ${formatBytes(status.localBytes)}")
                },
            )
            if (status.needFiles > 0 || status.needBytes > 0) {
                ListItem(
                    headlineContent = { Text("Out of Sync") },
                    supportingContent = {
                        Text("${status.needFiles} files, ${formatBytes(status.needBytes)}")
                    },
                )
            }
            if (status.pullErrors > 0) {
                ListItem(
                    headlineContent = { Text("Pull Errors") },
                    supportingContent = { Text("${status.pullErrors} error(s)") },
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        supportingColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
            ListItem(
                headlineContent = { Text("Shared Devices") },
                supportingContent = { Text("${folder.devices.size} device(s)") },
            )

            if (onConditionsChanged != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Sync Conditions",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                ListItem(
                    headlineContent = { Text("Wi-Fi only") },
                    supportingContent = { Text("Pause this folder when not on Wi-Fi") },
                    leadingContent = { Icon(Icons.Default.Wifi, null) },
                    trailingContent = {
                        ToggleButton(
                            checked = wifiOnly,
                            onCheckedChange = { onConditionsChanged(it, chargingOnly) },
                            shapes = ToggleButtonDefaults.shapes(),
                        ) { }
                    },
                )
                ListItem(
                    headlineContent = { Text("Charging only") },
                    supportingContent = { Text("Pause this folder when not charging") },
                    leadingContent = { Icon(Icons.Default.BatteryChargingFull, null) },
                    trailingContent = {
                        ToggleButton(
                            checked = chargingOnly,
                            onCheckedChange = { onConditionsChanged(wifiOnly, it) },
                            shapes = ToggleButtonDefaults.shapes(),
                        ) { }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))

            // Remove folder
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
                Text("Remove Folder")
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // Delete confirmation with impact preview
    if (showDeleteDialog && folder != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Remove folder?") },
            text = {
                Column {
                    Text("This will stop syncing \"${folder.label.ifBlank { folder.id }}\".")
                    Spacer(Modifier.height(8.dp))
                    if (status != null) {
                        Text(
                            "Impact: ${status.localFiles} files (${formatBytes(status.localBytes)}) will stop syncing across ${folder.devices.size} device(s).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(
                        "Files on this device will not be deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    onRemove?.invoke(folder.id)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (showRepairDialog && folder != null) {
        AlertDialog(
            onDismissRequest = { showRepairDialog = false },
            title = { Text("Repair folder index?") },
            text = {
                Column {
                    Text("This pauses the folder, resets Syncthing's local index for it, then rescans.")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Files are not deleted. Use this when devices show stuck remote completion even though the files already match.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRepairDialog = false
                    onRepairIndex?.invoke(folder.id)
                }) { Text("Repair") }
            },
            dismissButton = {
                TextButton(onClick = { showRepairDialog = false }) { Text("Cancel") }
            },
        )
    }
}

private fun folderTypeLabel(type: String): String = when (type) {
    "sendreceive" -> "Send & Receive"
    "sendonly" -> "Send Only"
    "receiveonly" -> "Receive Only"
    "receiveencrypted" -> "Receive Encrypted"
    else -> type
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

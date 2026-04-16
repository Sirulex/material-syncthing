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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import dev.lostf1sh.syncthing.ui.core.components.StatusChip

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderDetailScreen(
    folder: Folder?,
    status: FolderStatus?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(folder?.label?.ifBlank { folder.id } ?: "Folder") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        if (folder == null || status == null) {
            // Expressive loading state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(48.dp),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // Status + action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(state = status.state)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Expressive buttons with animated shapes
                    FilledTonalButton(
                        onClick = { /* rescan */ },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Rescan")
                    }

                    FilledTonalButton(
                        onClick = { /* pause/resume */ },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(
                            if (folder.paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(if (folder.paused) "Resume" else "Pause")
                    }
                }
            }

            // Expressive wavy sync progress
            if (status.state == "syncing" && status.globalBytes > 0) {
                Spacer(Modifier.height(12.dp))
                val progress = status.inSyncBytes.toFloat() / status.globalBytes.toFloat()
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(progress * 100).toInt()}% synced",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Details
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
            if (status.needFiles > 0) {
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
                    supportingContent = {
                        Text("${status.pullErrors} error(s)")
                    },
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        supportingColor = MaterialTheme.colorScheme.error,
                    ),
                )
            }
            ListItem(
                headlineContent = { Text("Shared Devices") },
                supportingContent = { Text("${folder.devices.size} device(s)") },
            )

            Spacer(Modifier.height(32.dp))
        }
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

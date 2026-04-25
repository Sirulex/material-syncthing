package dev.lostf1sh.syncthing.ui.folders

import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ripple
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.api.dto.FolderStatus
import dev.lostf1sh.syncthing.ui.core.components.EmptyState
import dev.lostf1sh.syncthing.ui.core.components.StatusChip
import dev.lostf1sh.syncthing.ui.core.format.formatBytes

private enum class FolderFilter { All, Syncing, Paused }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoldersScreen(
    folders: List<Folder>,
    folderStates: Map<String, String>,
    folderStatuses: Map<String, FolderStatus> = emptyMap(),
    onFolderClick: (String) -> Unit,
    onAddFolder: (() -> Unit)? = null,
    onTogglePause: ((String, Boolean) -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf(FolderFilter.All) }

    val filteredFolders = remember(folders, folderStates, selectedFilter) {
        folders.filter { folder ->
            when (selectedFilter) {
                FolderFilter.All -> true
                FolderFilter.Syncing -> (folderStates[folder.id] ?: "unknown") == "syncing" && !folder.paused
                FolderFilter.Paused -> folder.paused
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
                            selected = selectedFilter == FolderFilter.All,
                            onClick = { selectedFilter = FolderFilter.All },
                            label = { Text(stringResource(R.string.filter_all)) },
                        )
                        FilterChip(
                            selected = selectedFilter == FolderFilter.Syncing,
                            onClick = { selectedFilter = FolderFilter.Syncing },
                            label = { Text(stringResource(R.string.filter_syncing)) },
                        )
                        FilterChip(
                            selected = selectedFilter == FolderFilter.Paused,
                            onClick = { selectedFilter = FolderFilter.Paused },
                            label = { Text(stringResource(R.string.filter_paused)) },
                        )
                    }
                }
                if (refreshing && folders.isEmpty()) {
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
                } else if (filteredFolders.isEmpty()) {
                    item {
                        EmptyState(
                            title = if (folders.isEmpty()) stringResource(R.string.no_folders) else stringResource(R.string.no_matching_folders),
                            description = if (folders.isEmpty()) {
                                stringResource(R.string.no_folders_description)
                            } else {
                                stringResource(R.string.no_matching_folders_description)
                            },
                            actionLabel = stringResource(R.string.add_folder),
                            onAction = onAddFolder,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                }
                items(filteredFolders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        state = folderStates[folder.id] ?: "unknown",
                        status = folderStatuses[folder.id],
                        onClick = { onFolderClick(folder.id) },
                        onTogglePause = onTogglePause?.let { toggle ->
                            { paused -> toggle(folder.id, paused) }
                        },
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }

        // Expressive Large Extended FAB
        if (onAddFolder != null) {
            MediumExtendedFloatingActionButton(
                onClick = onAddFolder,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = stringResource(R.string.cd_add_folder),
                    )
                },
                text = { Text(stringResource(R.string.add_folder)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderCard(
    folder: Folder,
    state: String,
    status: FolderStatus?,
    onClick: () -> Unit,
    onTogglePause: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isSyncing = state == "syncing"
    val globalBytes = status?.globalBytes ?: 0L
    val inSyncBytes = status?.inSyncBytes ?: 0L
    val hasProgress = isSyncing && globalBytes > 0 && !folder.paused

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
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Expressive icon toggle for pause/resume
                if (onTogglePause != null) {
                    val haptic = LocalHapticFeedback.current
                    FilledIconToggleButton(
                        checked = !folder.paused,
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onTogglePause(!it)
                        },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (folder.paused) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.cd_resume_folder))
                        } else {
                            Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.cd_pause_folder))
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folder.label.ifBlank { folder.id },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                StatusChip(state = if (folder.paused) "paused" else state)
                if (hasProgress) {
                    val progress = (inSyncBytes.toFloat() / globalBytes.toFloat()).coerceIn(0f, 1f)
                    CircularWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Real progress bar when syncing with known bytes
            if (hasProgress) {
                Spacer(Modifier.height(8.dp))
                val progress = (inSyncBytes.toFloat() / globalBytes.toFloat()).coerceIn(0f, 1f)
                LinearWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "${(progress * 100).toInt()}% — ${dev.lostf1sh.syncthing.ui.core.format.formatBytes(inSyncBytes)} / ${dev.lostf1sh.syncthing.ui.core.format.formatBytes(globalBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (isSyncing && !folder.paused) {
                // Indeterminate wavy progress when syncing but bytes unknown
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}



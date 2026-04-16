package dev.lostf1sh.syncthing.ui.folders

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import dev.lostf1sh.syncthing.api.dto.Folder
import dev.lostf1sh.syncthing.ui.core.components.EmptyState
import dev.lostf1sh.syncthing.ui.core.components.StatusChip

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FoldersScreen(
    folders: List<Folder>,
    folderStates: Map<String, String>,
    onFolderClick: (String) -> Unit,
    onAddFolder: (() -> Unit)? = null,
    onTogglePause: ((String, Boolean) -> Unit)? = null,
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (folders.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            EmptyState(
                title = "No folders",
                description = "Add a folder to start syncing files between devices.",
                actionLabel = "Add Folder",
                onAction = onAddFolder,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 }
    }
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
                items(folders, key = { it.id }) { folder ->
                    FolderCard(
                        folder = folder,
                        state = folderStates[folder.id] ?: "unknown",
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
            LargeExtendedFloatingActionButton(
                onClick = onAddFolder,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = {
                    Icon(
                        Icons.Default.CreateNewFolder,
                        contentDescription = null,
                    )
                },
                text = { Text("Add Folder") },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FolderCard(
    folder: Folder,
    state: String,
    onClick: () -> Unit,
    onTogglePause: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isSyncing = state == "syncing"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
                    FilledIconToggleButton(
                        checked = !folder.paused,
                        onCheckedChange = { onTogglePause(!it) },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        modifier = Modifier.size(40.dp),
                    ) {
                        if (folder.paused) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                        } else {
                            Icon(Icons.Default.Folder, contentDescription = "Pause")
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
            }

            // Expressive wavy progress when syncing
            if (isSyncing && !folder.paused) {
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

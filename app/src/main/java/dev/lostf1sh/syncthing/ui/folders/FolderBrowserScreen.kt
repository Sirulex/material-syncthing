package dev.lostf1sh.syncthing.ui.folders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.api.dto.BrowseEntry
import dev.lostf1sh.syncthing.ui.core.components.EmptyState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderBrowserScreen(
    folderLabel: String,
    prefix: String,
    entries: List<BrowseEntry>,
    pendingPaths: Set<String>,
    loading: Boolean,
    onBack: () -> Unit,
    onRefresh: suspend () -> Unit,
    onOpenDirectory: (String) -> Unit,
    onRescan: (String) -> Unit,
    onCopyPath: (String) -> Unit,
    onAddToIgnores: (String) -> Unit,
    onEditIgnores: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<BrowseEntry?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val crumbs = remember(prefix) { prefix.split('/').filter { it.isNotEmpty() } }

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Column {
                        Text(folderLabel.ifBlank { "Browse" })
                        if (crumbs.isNotEmpty()) {
                            Text(
                                "/${crumbs.joinToString("/")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onEditIgnores, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.Default.Description, contentDescription = "Ignores")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            BreadcrumbRow(
                crumbs = crumbs,
                onCrumbClick = { depth ->
                    val newPrefix = crumbs.take(depth).joinToString("/")
                    onOpenDirectory(newPrefix)
                },
            )

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    scope.launch {
                        refreshing = true
                        try { onRefresh() } finally { refreshing = false }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (loading && entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (entries.isEmpty()) {
                    EmptyState(
                        title = "Empty",
                        description = "This directory has no tracked files.",
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(entries, key = { "${it.type}-${it.name}" }) { entry ->
                            BrowseEntryCard(
                                entry = entry,
                                isPending = pendingPaths.contains(joinPath(prefix, entry.name)),
                                onClick = {
                                    if (entry.isDirectory) {
                                        onOpenDirectory(joinPath(prefix, entry.name))
                                    } else {
                                        selectedEntry = entry
                                    }
                                },
                                onLongClick = { selectedEntry = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedEntry?.let { entry ->
        val fullPath = joinPath(prefix, entry.name)
        ModalBottomSheet(
            onDismissRequest = { selectedEntry = null },
            sheetState = sheetState,
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (entry.isDirectory) "Directory" else formatBrowseBytes(entry.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(
                    onClick = {
                        onRescan(fullPath)
                        selectedEntry = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        if (entry.isDirectory) "Rescan this directory" else "Rescan this file",
                        modifier = Modifier.weight(1f),
                    )
                }
                TextButton(
                    onClick = {
                        onCopyPath(fullPath)
                        selectedEntry = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Copy path", modifier = Modifier.weight(1f))
                }
                TextButton(
                    onClick = {
                        onAddToIgnores(fullPath)
                        selectedEntry = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Add to ignores", modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun BreadcrumbRow(
    crumbs: List<String>,
    onCrumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        IconButton(
            onClick = { onCrumbClick(0) },
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(Icons.Default.Home, contentDescription = "Root")
        }
        crumbs.forEachIndexed { index, crumb ->
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { onCrumbClick(index + 1) }) {
                Text(crumb, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BrowseEntryCard(
    entry: BrowseEntry,
    isPending: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isPending) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                contentDescription = null,
                tint = if (entry.isDirectory) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Column(Modifier.weight(1f)) {
                Text(
                    entry.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = buildString {
                    if (!entry.isDirectory) append(formatBrowseBytes(entry.size))
                    if (entry.modTime.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(entry.modTime.take(10))
                    }
                }
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isPending) {
                Box(
                    Modifier
                        .size(10.dp)
                        .padding(2.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Pending",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
            if (entry.isDirectory) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun joinPath(prefix: String, leaf: String): String =
    if (prefix.isEmpty()) leaf else "$prefix/$leaf"

private fun formatBrowseBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KiB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MiB".format(mb)
    val gb = mb / 1024.0
    return "%.1f GiB".format(gb)
}

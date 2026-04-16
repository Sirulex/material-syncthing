package dev.lostf1sh.syncthing.ui.errors

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.data.model.SyncIssue
import dev.lostf1sh.syncthing.ui.core.components.EmptyState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ErrorCenterScreen(
    issues: List<SyncIssue>,
    onBack: () -> Unit,
    onRetry: ((SyncIssue) -> Unit)? = null,
    onRescan: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Error Center") },
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
        if (issues.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = "All clear",
                    description = "No issues detected. Sync is running smoothly.",
                    icon = Icons.Default.CheckCircle,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(innerPadding),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(issues, key = { it.id }) { issue ->
                IssueCard(
                    issue = issue,
                    onRetry = { onRetry?.invoke(issue) },
                    onRescan = { issue.folderId?.let { id -> onRescan?.invoke(id) } },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IssueCard(
    issue: SyncIssue,
    onRetry: () -> Unit,
    onRescan: () -> Unit,
) {
    val icon = when (issue.type) {
        SyncIssue.Type.PULL_ERROR -> Icons.Default.Warning
        SyncIssue.Type.PERMISSION -> Icons.Default.Storage
        SyncIssue.Type.DISK_FULL -> Icons.Default.Storage
        SyncIssue.Type.FOLDER_ERROR -> Icons.Default.Error
        else -> Icons.Default.Error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        // Expressive: extra-large rounded card
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    issue.type.name.replace("_", " "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(issue.message, style = MaterialTheme.typography.bodyMedium)
            issue.path?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                if (issue.folderId != null) {
                    FilledTonalButton(
                        onClick = onRescan,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(Icons.Default.Refresh, null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Rescan")
                    }
                }
            }
        }
    }
}

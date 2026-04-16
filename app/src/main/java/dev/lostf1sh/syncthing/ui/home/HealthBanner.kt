package dev.lostf1sh.syncthing.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.data.model.SyncHealth

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HealthBanner(
    health: SyncHealth,
    modifier: Modifier = Modifier,
) {
    val (icon, label, tint) = healthDisplay(health.overall)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = tint.copy(alpha = 0.12f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon, null,
                tint = tint,
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                )
                Text(
                    "${health.folderCount} folders, ${health.connectedDevices}/${health.deviceCount} devices",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (health.issues.isNotEmpty()) {
                Text(
                    "${health.issues.size} issue(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun healthDisplay(status: SyncHealth.Status): Triple<ImageVector, String, androidx.compose.ui.graphics.Color> {
    return when (status) {
        SyncHealth.Status.UP_TO_DATE -> Triple(
            Icons.Default.CheckCircle, "Up to Date", MaterialTheme.colorScheme.primary
        )
        SyncHealth.Status.SYNCING -> Triple(
            Icons.Default.Sync, "Syncing", MaterialTheme.colorScheme.tertiary
        )
        SyncHealth.Status.SCANNING -> Triple(
            Icons.Default.Cloud, "Scanning", MaterialTheme.colorScheme.secondary
        )
        SyncHealth.Status.ERROR -> Triple(
            Icons.Default.Error, "Error", MaterialTheme.colorScheme.error
        )
        SyncHealth.Status.PAUSED -> Triple(
            Icons.Default.Pause, "Paused", MaterialTheme.colorScheme.outline
        )
        SyncHealth.Status.STOPPED -> Triple(
            Icons.Default.Pause, "Stopped", MaterialTheme.colorScheme.outline
        )
    }
}

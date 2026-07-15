package dev.sirulex.syncthing.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.sirulex.syncthing.data.model.SyncHealth

/**
 * Expressive overview card — tinted primary surface, four at-a-glance stats.
 * Click opens diagnostics. Shown above HealthBanner.
 */
@Composable
fun SyncOverviewCard(
    health: SyncHealth,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.primaryContainer.copy(alpha = 0.55f),
            contentColor = scheme.onPrimaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Overview",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onPrimaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(
                    icon = Icons.Default.Folder,
                    value = health.folderCount.toString(),
                    label = "Folders",
                    tint = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Default.Devices,
                    value = "${health.connectedDevices}/${health.deviceCount}",
                    label = "Devices",
                    tint = scheme.secondary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Default.Sync,
                    value = health.syncingFolders.toString(),
                    label = "Syncing",
                    tint = scheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = Icons.Default.ErrorOutline,
                    value = health.issues.size.toString(),
                    label = "Issues",
                    tint = if (health.issues.isEmpty()) scheme.outline else scheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .padding(0.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
        )
    }
}

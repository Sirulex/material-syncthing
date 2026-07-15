package dev.sirulex.syncthing.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ripple

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentChangesCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                onClick = onClick,
            ),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.tertiaryContainer.copy(alpha = 0.55f),
            contentColor = scheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Recent Changes",
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onTertiaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.weight(1f),
                )
                SmallExtendedFloatingActionButton(
                    onClick = onClick,
                    icon = { Icon(Icons.Default.History, contentDescription = null) },
                    text = { Text("View") },
                )
            }
            Spacer(Modifier.height(12.dp))
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = scheme.tertiary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "View recent file sync activity",
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onTertiaryContainer,
            )
        }
    }
}

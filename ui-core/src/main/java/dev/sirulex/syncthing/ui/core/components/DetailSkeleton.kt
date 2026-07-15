package dev.sirulex.syncthing.ui.core.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Skeleton layout approximating the detail-screen shape while data loads.
 * Shows shimmer rows matching a ListItem layout.
 */
@Composable
fun DetailSkeleton(
    modifier: Modifier = Modifier,
    rowCount: Int = 5,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Action row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShimmerBox(
                modifier = Modifier
                    .height(48.dp)
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
            )
            ShimmerBox(
                modifier = Modifier
                    .height(48.dp)
                    .weight(1f),
                shape = RoundedCornerShape(24.dp),
            )
        }

        Spacer(Modifier.height(4.dp))

        // List rows
        repeat(rowCount) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShimmerCircle(size = 40.dp)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ShimmerLine(widthFraction = 0.4f, height = 14.dp)
                    ShimmerLine(widthFraction = 0.8f, height = 12.dp)
                }
            }
        }
    }
}

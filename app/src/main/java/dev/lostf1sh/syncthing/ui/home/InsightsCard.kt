package dev.lostf1sh.syncthing.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.lostf1sh.syncthing.data.model.BandwidthSample

@Composable
fun InsightsCard(
    bandwidth: List<BandwidthSample>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val latest = bandwidth.lastOrNull()
    val maxBps = bandwidth.maxOfOrNull { maxOf(it.inBytesPerSec, it.outBytesPerSec) } ?: 0L

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = scheme.secondaryContainer.copy(alpha = 0.55f),
            contentColor = scheme.onSecondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Throughput",
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThroughputTile(
                    icon = Icons.Default.Download,
                    label = "In",
                    value = formatRate(latest?.inBytesPerSec ?: 0),
                    tint = scheme.primary,
                    modifier = Modifier.weight(1f),
                )
                ThroughputTile(
                    icon = Icons.Default.Upload,
                    label = "Out",
                    value = formatRate(latest?.outBytesPerSec ?: 0),
                    tint = scheme.tertiary,
                    modifier = Modifier.weight(1f),
                )
            }
            if (bandwidth.size >= 2) {
                Spacer(Modifier.height(12.dp))
                Sparkline(
                    samples = bandwidth,
                    maxBps = maxBps,
                    inColor = scheme.primary,
                    outColor = scheme.tertiary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
            }
        }
    }
}

@Composable
private fun ThroughputTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Column {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun Sparkline(
    samples: List<BandwidthSample>,
    maxBps: Long,
    inColor: Color,
    outColor: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (samples.size < 2 || maxBps <= 0) return@Canvas
        val stepX = size.width / (samples.size - 1).toFloat()
        fun toY(v: Long): Float {
            val scaled = (v.toFloat() / maxBps.toFloat()).coerceIn(0f, 1f)
            return size.height - (scaled * size.height)
        }

        val inPath = Path().apply {
            moveTo(0f, toY(samples[0].inBytesPerSec))
            samples.forEachIndexed { i, s ->
                lineTo(i * stepX, toY(s.inBytesPerSec))
            }
        }
        val outPath = Path().apply {
            moveTo(0f, toY(samples[0].outBytesPerSec))
            samples.forEachIndexed { i, s ->
                lineTo(i * stepX, toY(s.outBytesPerSec))
            }
        }

        drawPath(
            path = inPath,
            color = inColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
        drawPath(
            path = outPath,
            color = outColor,
            style = Stroke(width = 3f, cap = StrokeCap.Round),
        )
    }
}

private fun formatRate(bps: Long): String {
    if (bps < 1024) return "$bps B/s"
    val kb = bps / 1024.0
    if (kb < 1024) return "%.1f KiB/s".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MiB/s".format(mb)
    return "%.1f GiB/s".format(mb / 1024.0)
}

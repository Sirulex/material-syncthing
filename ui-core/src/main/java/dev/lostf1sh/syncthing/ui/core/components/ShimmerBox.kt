package dev.lostf1sh.syncthing.ui.core.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Expressive shimmer block — tinted base + sweeping highlight.
 * Use as leading icon, progress bar, or text-line placeholder.
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val progress by transition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-progress",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(base)
            .drawWithContent {
                drawContent()
                val width = size.width
                val startX = width * (progress - 0.3f)
                val endX = width * (progress + 0.3f)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            highlight.copy(alpha = 0.6f),
                            Color.Transparent,
                        ),
                        start = Offset(startX, 0f),
                        end = Offset(endX, size.height),
                    ),
                    blendMode = BlendMode.SrcAtop,
                )
            },
    )
}

@Composable
fun ShimmerLine(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    widthFraction: Float = 1f,
) {
    ShimmerBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height),
        shape = RoundedCornerShape(999.dp),
    )
}

@Composable
fun ShimmerCircle(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    ShimmerBox(
        modifier = modifier.size(size),
        shape = CircleShape,
    )
}

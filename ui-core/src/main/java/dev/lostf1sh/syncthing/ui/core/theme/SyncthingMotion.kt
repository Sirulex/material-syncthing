package dev.lostf1sh.syncthing.ui.core.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object SyncthingMotion {
    val navFadeIn = tween<Float>(durationMillis = 320, easing = LinearOutSlowInEasing)
    val navFadeOut = tween<Float>(durationMillis = 260, easing = FastOutSlowInEasing)
    val navSlide = tween<IntOffset>(durationMillis = 420, easing = FastOutSlowInEasing)

    val tabScale = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    val tabOffset = tween<Float>(durationMillis = 220)
}

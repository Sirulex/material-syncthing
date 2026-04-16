package dev.lostf1sh.syncthing.ui.core.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import dev.lostf1sh.syncthing.ui.core.R

/**
 * Google Sans Flex — variable font bundled in res/font/gflex_variable.ttf.
 *
 * Axes used:
 *  - wght (100..1000): weight
 *  - ROND (0..100):    roundness — 100 = fully rounded letterforms
 *
 * Shipped inside the APK so every device renders the same, regardless of OS version.
 */
private const val ROND = 100f

@OptIn(ExperimentalTextApi::class)
private fun gflex(weight: FontWeight) = Font(
    resId = R.font.gflex_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight.weight),
        FontVariation.Setting("ROND", ROND),
    ),
)

private val GoogleSansFlexRounded: FontFamily = FontFamily(
    gflex(FontWeight.Light),
    gflex(FontWeight.Normal),
    gflex(FontWeight.Medium),
    gflex(FontWeight.SemiBold),
    gflex(FontWeight.Bold),
    gflex(FontWeight.ExtraBold),
    gflex(FontWeight.Black),
)

val SyncthingTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 45.sp,
        lineHeight = 52.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        lineHeight = 44.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlexRounded,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

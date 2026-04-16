package dev.lostf1sh.syncthing.ui.core.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Google Sans Flex — variable font bundled on Android 14+ (API 34).
 *
 * Axes used:
 *  - wght (100..1000): weight
 *  - ROND (0..100):    roundness  — 100 = fully rounded letterforms
 *  - opsz (auto):      optical sizing hint (matches point size)
 *  - GRAD (-200..150): grade (contrast)
 *
 * On older devices the family name resolves to the platform default (sans-serif).
 * Variation settings are ignored by non-variable fallbacks but cause no crash.
 */
@OptIn(ExperimentalTextApi::class)
private fun googleSansFlex(
    weight: Int,
    roundness: Float = 100f,
    grade: Int = 0,
    opticalSize: Float = 14f,
): Font = Font(
    familyName = DeviceFontFamilyName("google-sans-flex"),
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(weight),
        FontVariation.grade(grade),
        FontVariation.Setting("ROND", roundness),
        FontVariation.Setting("opsz", opticalSize),
    ),
)

private val GoogleSansFlexRounded: FontFamily =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        FontFamily(
            googleSansFlex(300),
            googleSansFlex(400),
            googleSansFlex(500),
            googleSansFlex(600),
            googleSansFlex(700),
            googleSansFlex(800),
            googleSansFlex(900),
        )
    } else {
        FontFamily.SansSerif
    }

// Slightly open letterforms pair well with rounded shapes.
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

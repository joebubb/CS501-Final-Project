package com.pictoteam.pictonote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Define the base typography with default sizes
val BaseTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp, // Default base size
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp, // Slightly larger than bodyLarge
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp, // Smaller than bodyLarge
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelMedium = TextStyle( // Used by Button text, similar to bodyMedium
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp
    )
    // Add any other styles you actively use, define their base relationship
)

// Function to create Typography scaled by a multiplier derived from the base font size setting
// It takes the multiplier (calculated in Theme.kt) and scales the BaseTypography
fun createScaledTypography(multiplier: Float): Typography {
    // Ensure multiplier is reasonable (e.g., avoid zero or negative)
    val safeMultiplier = multiplier.coerceAtLeast(0.5f) // Prevent excessively small text

    return Typography(
        bodyLarge = BaseTypography.bodyLarge.copy(fontSize = BaseTypography.bodyLarge.fontSize * safeMultiplier, lineHeight = BaseTypography.bodyLarge.lineHeight * safeMultiplier),
        titleLarge = BaseTypography.titleLarge.copy(fontSize = BaseTypography.titleLarge.fontSize * safeMultiplier, lineHeight = BaseTypography.titleLarge.lineHeight * safeMultiplier),
        headlineMedium = BaseTypography.headlineMedium.copy(fontSize = BaseTypography.headlineMedium.fontSize * safeMultiplier, lineHeight = BaseTypography.headlineMedium.lineHeight * safeMultiplier),
        headlineSmall = BaseTypography.headlineSmall.copy(fontSize = BaseTypography.headlineSmall.fontSize * safeMultiplier, lineHeight = BaseTypography.headlineSmall.lineHeight * safeMultiplier),
        titleMedium = BaseTypography.titleMedium.copy(fontSize = BaseTypography.titleMedium.fontSize * safeMultiplier, lineHeight = BaseTypography.titleMedium.lineHeight * safeMultiplier),
        bodyMedium = BaseTypography.bodyMedium.copy(fontSize = BaseTypography.bodyMedium.fontSize * safeMultiplier, lineHeight = BaseTypography.bodyMedium.lineHeight * safeMultiplier),
        bodySmall = BaseTypography.bodySmall.copy(fontSize = BaseTypography.bodySmall.fontSize * safeMultiplier, lineHeight = BaseTypography.bodySmall.lineHeight * safeMultiplier),
        labelMedium = BaseTypography.labelMedium.copy(fontSize = BaseTypography.labelMedium.fontSize * safeMultiplier, lineHeight = BaseTypography.labelMedium.lineHeight * safeMultiplier),
        // Scale other styles defined in BaseTypography similarly
    )
}
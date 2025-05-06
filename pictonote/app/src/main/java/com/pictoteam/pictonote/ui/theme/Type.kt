// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/ui/theme/Type.kt
package com.pictoteam.pictonote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable // Add if not present
import androidx.compose.runtime.ReadOnlyComposable // Add if not present
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pictoteam.pictonote.R // Assuming your font resources are in res/font
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager // For default font size

// Define cozy paper font families (if you are using custom fonts)
val CaveatFontFamily = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_bold, FontWeight.Bold)
)

val NotoSerifFontFamily = FontFamily(
    Font(R.font.noto_serif_regular, FontWeight.Normal),
    Font(R.font.noto_serif_bold, FontWeight.Bold),
    Font(R.font.noto_serif_italic, FontWeight.Normal, style = FontStyle.Italic)
)

// Base Typography - these are the default sizes before scaling
private val BaseTypography = Typography(
    displayLarge = TextStyle(fontFamily = CaveatFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    displayMedium = TextStyle(fontFamily = CaveatFontFamily, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
    displaySmall = TextStyle(fontFamily = CaveatFontFamily, fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    headlineLarge = TextStyle(fontFamily = CaveatFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
    headlineMedium = TextStyle(fontFamily = CaveatFontFamily, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = CaveatFontFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = NotoSerifFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp)
)

// Function to create Typography scaled by the chosen base font size
@Composable
@ReadOnlyComposable
fun createScaledTypography(currentBaseFontSizeSp: Float): Typography {
    val defaultBaseFontSizeSp = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP
    val scaleFactor = if (defaultBaseFontSizeSp > 0f) currentBaseFontSizeSp / defaultBaseFontSizeSp else 1f

    return Typography(
        displayLarge = BaseTypography.displayLarge.copy(fontSize = BaseTypography.displayLarge.fontSize * scaleFactor, lineHeight = BaseTypography.displayLarge.lineHeight * scaleFactor),
        displayMedium = BaseTypography.displayMedium.copy(fontSize = BaseTypography.displayMedium.fontSize * scaleFactor, lineHeight = BaseTypography.displayMedium.lineHeight * scaleFactor),
        displaySmall = BaseTypography.displaySmall.copy(fontSize = BaseTypography.displaySmall.fontSize * scaleFactor, lineHeight = BaseTypography.displaySmall.lineHeight * scaleFactor),
        headlineLarge = BaseTypography.headlineLarge.copy(fontSize = BaseTypography.headlineLarge.fontSize * scaleFactor, lineHeight = BaseTypography.headlineLarge.lineHeight * scaleFactor),
        headlineMedium = BaseTypography.headlineMedium.copy(fontSize = BaseTypography.headlineMedium.fontSize * scaleFactor, lineHeight = BaseTypography.headlineMedium.lineHeight * scaleFactor),
        titleLarge = BaseTypography.titleLarge.copy(fontSize = BaseTypography.titleLarge.fontSize * scaleFactor, lineHeight = BaseTypography.titleLarge.lineHeight * scaleFactor),
        titleMedium = BaseTypography.titleMedium.copy(fontSize = BaseTypography.titleMedium.fontSize * scaleFactor, lineHeight = BaseTypography.titleMedium.lineHeight * scaleFactor),
        titleSmall = BaseTypography.titleSmall.copy(fontSize = BaseTypography.titleSmall.fontSize * scaleFactor, lineHeight = BaseTypography.titleSmall.lineHeight * scaleFactor),
        bodyLarge = BaseTypography.bodyLarge.copy(fontSize = BaseTypography.bodyLarge.fontSize * scaleFactor, lineHeight = BaseTypography.bodyLarge.lineHeight * scaleFactor),
        bodyMedium = BaseTypography.bodyMedium.copy(fontSize = BaseTypography.bodyMedium.fontSize * scaleFactor, lineHeight = BaseTypography.bodyMedium.lineHeight * scaleFactor),
        bodySmall = BaseTypography.bodySmall.copy(fontSize = BaseTypography.bodySmall.fontSize * scaleFactor, lineHeight = BaseTypography.bodySmall.lineHeight * scaleFactor),
        labelLarge = BaseTypography.labelLarge.copy(fontSize = BaseTypography.labelLarge.fontSize * scaleFactor, lineHeight = BaseTypography.labelLarge.lineHeight * scaleFactor),
        labelMedium = BaseTypography.labelMedium.copy(fontSize = BaseTypography.labelMedium.fontSize * scaleFactor, lineHeight = BaseTypography.labelMedium.lineHeight * scaleFactor),
        labelSmall = BaseTypography.labelSmall.copy(fontSize = BaseTypography.labelSmall.fontSize * scaleFactor, lineHeight = BaseTypography.labelSmall.lineHeight * scaleFactor)
    )
}

// The createCozyTypography function can be kept if you switch between typography sets,
// but for dynamic sizing, createScaledTypography is the primary one.
// If you only use one set of font families, you can simplify this.
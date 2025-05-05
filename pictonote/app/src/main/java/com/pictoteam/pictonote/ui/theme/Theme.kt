package com.pictoteam.pictonote.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
// Removed unused imports: Typography, Color, TextStyle, FontFamily, FontWeight, sp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager // Import for default font size

// --- Keep Color Scheme definitions ---
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
    // Add other overrides if needed
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
    // Add other overrides if needed
)
// --- End Color Scheme definitions ---


@Composable
fun PictoNoteTheme(
    darkTheme: Boolean, // Passed from ViewModel/DataStore
    baseFontSize: Float = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP, // Passed from ViewModel/DataStore
    dynamicColor: Boolean = false, // Changed default to false to match Jorge's branch
    content: @Composable () -> Unit
) {
    // 1. Determine Color Scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // 2. Calculate Font Size Multiplier
    // Divide the desired base font size (from settings) by the default base font size (defined in BaseTypography, e.g., 16.sp)
    val defaultBaseSp = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP // Should match BaseTypography.bodyLarge.fontSize
    val fontSizeMultiplier = if (defaultBaseSp > 0) baseFontSize / defaultBaseSp else 1.0f

    // 3. Create Scaled Typography
    val typography = createScaledTypography(fontSizeMultiplier)

    // 4. Apply Theme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography, // Use the calculated, scaled typography
        content = content
    )
}
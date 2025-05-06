package com.pictoteam.pictonote.ui.theme

import android.os.Build
import kotlin.random.Random
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager // Import for default font size

// --- New Cozy Paper Color Schemes ---
private val CozyLightColorScheme = lightColorScheme(
    primary = WarmBrown,
    onPrimary = LightCream,
    primaryContainer = PaleYellow,
    onPrimaryContainer = DarkBrown,
    secondary = SoftOrange,
    onSecondary = LightCream,
    secondaryContainer = Cream,
    onSecondaryContainer = Rust,
    tertiary = MutedGreen,
    onTertiary = LightCream,
    tertiaryContainer = PaleYellow,
    onTertiaryContainer = DarkBrown,
    background = Cream,
    onBackground = DarkBrown,
    surface = LightCream,
    onSurface = DarkBrown,
    surfaceVariant = PaleYellow,
    onSurfaceVariant = WarmBrown,
)

private val CozyDarkColorScheme = darkColorScheme(
    primary = SoftOrange,
    onPrimary = Color(0xFF472C1B), // Darker brown
    primaryContainer = Color(0xFF704728), // Medium brown
    onPrimaryContainer = PaleYellow,
    secondary = WarmBrown,
    onSecondary = LightCream,
    secondaryContainer = Color(0xFF704728), // Medium brown
    onSecondaryContainer = PaleYellow,
    tertiary = MutedGreen,
    onTertiary = Color(0xFF2A2A2A), // Dark gray
    tertiaryContainer = Color(0xFF4D4D4D), // Medium gray
    onTertiaryContainer = PaleYellow,
    background = Color(0xFF2A2A2A), // Dark gray
    onBackground = LightCream,
    surface = Color(0xFF3D3D3D), // Medium gray
    onSurface = LightCream,
    surfaceVariant = Color(0xFF472C1B), // Darker brown
    onSurfaceVariant = PaleYellow,
)

// --- Paper Texture System ---
// Create a local for paper texture
val LocalPaperTexture = compositionLocalOf { PaperTexture.NONE }

// Enum for different paper textures
enum class PaperTexture {
    NONE,
    SUBTLE,
    MEDIUM,
    ROUGH
}

// --- Theme Functions ---
@Composable
fun PictoNoteTheme(
    darkTheme: Boolean, // Passed from ViewModel/DataStore
    baseFontSize: Float = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP, // Passed from ViewModel/DataStore
    dynamicColor: Boolean = false, // Changed to false by default to avoid potential resource conflicts
    useCozyTheme: Boolean = false, // New parameter to toggle cozy paper theme
    paperTexture: PaperTexture = PaperTexture.MEDIUM, // Paper texture level
    content: @Composable () -> Unit
) {
    // 1. Determine Color Scheme
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useCozyTheme && darkTheme -> CozyDarkColorScheme
        useCozyTheme -> CozyLightColorScheme
        darkTheme -> CozyDarkColorScheme
        else -> CozyLightColorScheme
    }

    // 2. Calculate Font Size Multiplier
    val defaultBaseSp = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP
    val fontSizeMultiplier = if (defaultBaseSp > 0) baseFontSize / defaultBaseSp else 1.0f

    // 3. Create Scaled Typography
    val typography = createScaledTypography(fontSizeMultiplier)

    // 4. Apply Theme with or without paper texture
    CompositionLocalProvider(LocalPaperTexture provides paperTexture) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = {
                if (useCozyTheme) {
                    PaperBackground(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                } else {
                    content()
                }
            }
        )
    }
}

// Paper background component that applies texture
@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    paperTexture: PaperTexture = LocalPaperTexture.current,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .paperTextureModifier(paperTexture)
    ) {
        content()
    }
}

// Extension function to apply paper texture
fun Modifier.paperTextureModifier(paperTexture: PaperTexture): Modifier {
    return when (paperTexture) {
        PaperTexture.NONE -> this
        PaperTexture.SUBTLE -> this.drawBehind {
            // Draw subtle noise pattern
            val noiseOpacity = 0.03f
            for (i in 0..200) {
                val x = Random.nextFloat() * size.width
                val y = Random.nextFloat() * size.height
                val radius = 1f + Random.nextFloat() * 1f // Random between 1-2
                drawCircle(
                    color = Color.Black.copy(alpha = noiseOpacity),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
        }
        PaperTexture.MEDIUM -> this.drawBehind {
            // Draw medium noise pattern
            val noiseOpacity = 0.05f
            for (i in 0..400) {
                val x = Random.nextFloat() * size.width
                val y = Random.nextFloat() * size.height
                val radius = 1f + Random.nextFloat() * 2f // Random between 1-3
                drawCircle(
                    color = Color.Black.copy(alpha = noiseOpacity),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
            // Add subtle gradient
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.03f)
                    ),
                    center = Offset(size.width / 2, size.height / 2),
                    radius = size.width * 0.7f
                )
            )
        }
        PaperTexture.ROUGH -> this.drawBehind {
            // Draw rough texture
            val noiseOpacity = 0.07f
            for (i in 0..600) {
                val x = Random.nextFloat() * size.width
                val y = Random.nextFloat() * size.height
                val radius = 1f + Random.nextFloat() * 3f // Random between 1-4
                drawCircle(
                    color = Color.Black.copy(alpha = noiseOpacity),
                    radius = radius,
                    center = Offset(x, y)
                )
            }
            // Add grain lines
            for (i in 0..40) {
                val y = Random.nextFloat() * size.height
                drawLine(
                    color = Color.Black.copy(alpha = 0.02f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 0.5f + Random.nextFloat() * 1.0f // Random between 0.5-1.5
                )
            }
        }
    }
}
// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/ui/theme/Theme.kt
package com.pictoteam.pictonote.ui.theme

import android.os.Build
import kotlin.random.Random // Keep if paperTextureModifier uses it
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable // For createScaledTypography if it's just reading
import androidx.compose.runtime.staticCompositionLocalOf // Changed from compositionLocalOf
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
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager

// Color schemes (CozyLightColorScheme, CozyDarkColorScheme) as before...
private val CozyLightColorScheme = lightColorScheme(
    primary = WarmBrown, onPrimary = LightCream, primaryContainer = PaleYellow, onPrimaryContainer = DarkBrown,
    secondary = SoftOrange, onSecondary = LightCream, secondaryContainer = Cream, onSecondaryContainer = Rust,
    tertiary = MutedGreen, onTertiary = LightCream, tertiaryContainer = PaleYellow, onTertiaryContainer = DarkBrown,
    background = Cream, onBackground = DarkBrown, surface = LightCream, onSurface = DarkBrown,
    surfaceVariant = PaleYellow, onSurfaceVariant = WarmBrown, error = Rust, onError = LightCream
)
private val CozyDarkColorScheme = darkColorScheme(
    primary = SoftOrange, onPrimary = Color(0xFF472C1B), primaryContainer = Color(0xFF704728), onPrimaryContainer = PaleYellow,
    secondary = WarmBrown, onSecondary = LightCream, secondaryContainer = Color(0xFF704728), onSecondaryContainer = PaleYellow,
    tertiary = MutedGreen, onTertiary = Color(0xFF2A2A2A), tertiaryContainer = Color(0xFF4D4D4D), onTertiaryContainer = PaleYellow,
    background = Color(0xFF2A2A2A), onBackground = LightCream, surface = Color(0xFF3D3D3D), onSurface = LightCream,
    surfaceVariant = Color(0xFF472C1B), onSurfaceVariant = PaleYellow, error = Color(0xFFFFB4AB), onError = Color(0xFF690005)
)


// Paper Texture System
val LocalPaperTexture = staticCompositionLocalOf { PaperTexture.NONE } // Use staticCompositionLocalOf if value rarely changes

enum class PaperTexture { NONE, SUBTLE, MEDIUM, ROUGH }

@Composable
fun PictoNoteTheme(
    darkTheme: Boolean,
    baseFontSize: Float = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP, // Default from DataStoreManager
    dynamicColor: Boolean = false,
    useCozyTheme: Boolean = true, // Defaulting to cozy theme as per your setup
    paperTexture: PaperTexture = PaperTexture.MEDIUM,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useCozyTheme && darkTheme -> CozyDarkColorScheme
        useCozyTheme -> CozyLightColorScheme
        darkTheme -> CozyDarkColorScheme // Fallback to CozyDark if not specific
        else -> CozyLightColorScheme    // Fallback to CozyLight
    }

    // Create Typography based on the current baseFontSize
    val typography = createScaledTypography(baseFontSize) // Pass the actual baseFontSize

    CompositionLocalProvider(LocalPaperTexture provides paperTexture) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography, // Use the dynamically scaled typography
            content = {
                if (useCozyTheme) {
                    PaperBackground(modifier = Modifier.fillMaxSize()) {
                        content()
                    }
                } else {
                    // Provide a default background if not using paper texture
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        content()
                    }
                }
            }
        )
    }
}

@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    paperTexture: PaperTexture = LocalPaperTexture.current,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .paperTextureModifier(paperTexture) // Apply texture if any
    ) {
        content()
    }
}

fun Modifier.paperTextureModifier(paperTexture: PaperTexture): Modifier {
    // paperTextureModifier implementation as before...
    return when (paperTexture) {
        PaperTexture.NONE -> this
        PaperTexture.SUBTLE -> this.drawBehind {
            val noiseOpacity = 0.03f
            for (i in 0..200) {
                val x = Random.nextFloat() * size.width; val y = Random.nextFloat() * size.height
                val radius = 1f + Random.nextFloat() * 1f
                drawCircle(Color.Black.copy(alpha = noiseOpacity), radius, Offset(x, y))
            }
        }
        PaperTexture.MEDIUM -> this.drawBehind {
            val noiseOpacity = 0.05f
            for (i in 0..400) {
                val x = Random.nextFloat() * size.width; val y = Random.nextFloat() * size.height
                val radius = 1f + Random.nextFloat() * 2f
                drawCircle(Color.Black.copy(alpha = noiseOpacity), radius, Offset(x, y))
            }
            drawRect(brush = Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.03f)), Offset(size.width / 2, size.height / 2), size.width * 0.7f))
        }
        PaperTexture.ROUGH -> this.drawBehind {
            val noiseOpacity = 0.07f
            for (i in 0..600) {
                val x = Random.nextFloat() * size.width; val y = Random.nextFloat() * size.height
                val radius = 1f + Random.nextFloat() * 3f
                drawCircle(Color.Black.copy(alpha = noiseOpacity), radius, Offset(x, y))
            }
            for (i in 0..40) {
                val y = Random.nextFloat() * size.height
                drawLine(Color.Black.copy(alpha = 0.02f), Offset(0f, y), Offset(size.width, y), 0.5f + Random.nextFloat() * 1.0f)
            }
        }
    }
}
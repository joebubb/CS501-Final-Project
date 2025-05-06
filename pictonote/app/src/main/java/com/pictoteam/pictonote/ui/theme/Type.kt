package com.pictoteam.pictonote.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pictoteam.pictonote.R

// Define standard font families (existing ones from your app)
// Keep your existing font definitions here

// Define cozy paper font families
// Note: You'll need to add these fonts to your res/font directory
val CaveatFontFamily = FontFamily(
    Font(R.font.caveat_regular, FontWeight.Normal),
    Font(R.font.caveat_bold, FontWeight.Bold)
)

val NotoSerifFontFamily = FontFamily(
    Font(R.font.noto_serif_regular, FontWeight.Normal),
    Font(R.font.noto_serif_bold, FontWeight.Bold),
    Font(R.font.noto_serif_italic, FontWeight.Normal, style = FontStyle.Italic)
)

// Your existing function to create scaled typography
@Composable
fun createScaledTypography(fontSizeMultiplier: Float): Typography {
    // Your existing code for standard typography
    return Typography(
        // Your existing TextStyle definitions, scaled by fontSizeMultiplier
        // ...
    )
}

annotation class Composable

// New function to create cozy paper typography
@Composable
fun createCozyTypography(fontSizeMultiplier: Float): Typography {
    return Typography(
        displayLarge = TextStyle(
            fontFamily = CaveatFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp * fontSizeMultiplier,
            lineHeight = 40.sp * fontSizeMultiplier,
            color = DarkBrown
        ),
        displayMedium = TextStyle(
            fontFamily = CaveatFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp * fontSizeMultiplier,
            lineHeight = 36.sp * fontSizeMultiplier,
            color = DarkBrown
        ),
        displaySmall = TextStyle(
            fontFamily = CaveatFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 24.sp * fontSizeMultiplier,
            lineHeight = 32.sp * fontSizeMultiplier,
            color = DarkBrown
        ),
        headlineLarge = TextStyle(
            fontFamily = CaveatFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp * fontSizeMultiplier,
            lineHeight = 28.sp * fontSizeMultiplier
        ),
        headlineMedium = TextStyle(
            fontFamily = CaveatFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp * fontSizeMultiplier,
            lineHeight = 26.sp * fontSizeMultiplier
        ),
        titleLarge = TextStyle(
            fontFamily = NotoSerifFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp * fontSizeMultiplier,
            lineHeight = 24.sp * fontSizeMultiplier
        ),
        titleMedium = TextStyle(
            fontFamily = NotoSerifFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp * fontSizeMultiplier,
            lineHeight = 22.sp * fontSizeMultiplier
        ),
        bodyLarge = TextStyle(
            fontFamily = NotoSerifFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp * fontSizeMultiplier,
            lineHeight = 24.sp * fontSizeMultiplier
        ),
        bodyMedium = TextStyle(
            fontFamily = NotoSerifFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp * fontSizeMultiplier,
            lineHeight = 20.sp * fontSizeMultiplier
        ),
        labelLarge = TextStyle(
            fontFamily = CaveatFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp * fontSizeMultiplier,
            lineHeight = 20.sp * fontSizeMultiplier
        )
    )
}
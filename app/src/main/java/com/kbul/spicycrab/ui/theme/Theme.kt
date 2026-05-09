package com.kbul.spicycrab.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = ForgeIron,
    onPrimary = Color.White,
    primaryContainer = Parchment,
    onPrimaryContainer = Coal,
    secondary = HammerGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD8E6DC),
    tertiary = Ember,
    onTertiary = Color.White,
    background = Mist,
    onBackground = Coal,
    surface = Color(0xFFFFFFFF),
    onSurface = Coal,
    surfaceVariant = Color(0xFFE2E8E1),
    onSurfaceVariant = Color(0xFF3E4A43),
    outline = Color(0xFF728078),
)

private val DarkColors = darkColorScheme(
    primary = TemperedGreen,
    onPrimary = Coal,
    primaryContainer = ForgeIron,
    onPrimaryContainer = Color(0xFFDCEADF),
    secondary = Color(0xFF9FBBAE),
    onSecondary = Coal,
    secondaryContainer = HammerGreen,
    tertiary = Color(0xFFE09A6A),
    onTertiary = Coal,
    background = Coal,
    onBackground = Color(0xFFE3EAE3),
    surface = Color(0xFF18211E),
    onSurface = Color(0xFFE3EAE3),
    surfaceVariant = Color(0xFF26322D),
    onSurfaceVariant = Color(0xFFC2CEC5),
    outline = Color(0xFF87958D),
)

@Composable
fun SpicyCrabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content
    )
}

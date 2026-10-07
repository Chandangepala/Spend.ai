package com.basic.spendai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    background = Forest10,
    onBackground = Green90,
    surface = Forest15,
    onSurface = Green90,
    surfaceContainerLow = Forest15,
)

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Color.White,
    primaryContainer = Green90,
    onPrimaryContainer = Green30,
    secondary = Teal40,
    secondaryContainer = Teal90,
    background = Mint95,
    onBackground = Ink,
    surface = Mint98,
    onSurface = Ink,
    onSurfaceVariant = InkVariant,
    surfaceContainerLow = Mint98,
)

private val ClayShapes = Shapes(
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Immutable
data class ClayPalette(val shadow: Color, val highlight: Color)

val LocalClayPalette = staticCompositionLocalOf {
    ClayPalette(shadow = ClayShadowLight, highlight = ClayHighlightLight)
}

@Composable
fun SpendaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default so the green financial motif is kept on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val clayPalette = if (darkTheme) {
        ClayPalette(shadow = ClayShadowDark, highlight = ClayHighlightDark)
    } else {
        ClayPalette(shadow = ClayShadowLight, highlight = ClayHighlightLight)
    }

    CompositionLocalProvider(LocalClayPalette provides clayPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ClayShapes,
            content = content
        )
    }
}

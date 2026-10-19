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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    // Snackbar action text (Material's default is purple).
    inversePrimary = Green40,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = Teal80,
    background = Forest10,
    onBackground = Green90,
    surface = Forest15,
    onSurface = Green90,
    onSecondaryContainer = Teal90,
    secondaryContainer = Color(0xFF1F4D47),
    // Material 3's container surfaces (nav bar, menus, sheets) default to a purple-grey; keep them forest.
    surfaceVariant = Color(0xFF2A3B2D),
    onSurfaceVariant = Color(0xFFB8CCBB),
    surfaceContainerLowest = Color(0xFF0B140C),
    surfaceContainerLow = Forest15,
    surfaceContainer = Color(0xFF1C2B1F),
    surfaceContainerHigh = Color(0xFF243527),
    surfaceContainerHighest = Color(0xFF2D402F),
    outline = Color(0xFF849886),
    outlineVariant = Color(0xFF3B4D3E),
)

private val LightColorScheme = lightColorScheme(
    primary = Emerald,
    onPrimary = Color.White,
    // Snackbar action text (Material's default is purple).
    inversePrimary = Green80,
    primaryContainer = NavPill,
    onPrimaryContainer = EmeraldDeep,
    secondary = Teal40,
    secondaryContainer = Teal90,
    background = ClayGround,
    onBackground = ClayInk,
    // White clay cards on the mint ground.
    surface = Color.White,
    onSurface = ClayInk,
    onSurfaceVariant = ClayMuted,
    // Material 3's container surfaces (nav bar, menus, sheets) default to a purple-grey; keep them mint.
    surfaceVariant = Color(0xFFDCE8DD),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = ClayCardTint,
    surfaceContainer = NavGround,
    // Dialogs: near-white like the clay cards, not a mint slab.
    surfaceContainerHigh = ClayCardTint,
    surfaceContainerHighest = Color(0xFFD2E5D5),
    outline = Color(0xFF718074),
    outlineVariant = Color(0xFFC2D1C5),
)

private val ClayShapes = Shapes(
    // Text fields use this; Material's 4dp default looked out of place among the clay shapes.
    extraSmall = RoundedCornerShape(16.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * Clay colours Material's scheme has no slot for: the shadow pair every clay surface casts, and the
 * emerald gradients of the hero card and primary buttons with the text that sits on them.
 */
@Immutable
data class ClayPalette(
    val shadow: Color,
    val highlight: Color,
    val hero: Brush = LightHero,
    val heroShadow: Color = Color(0xFF124D2B),
    val button: Brush = EmeraldButton,
    val onEmerald: Color = Color.White,
    /** Secondary text on emerald (labels, counts). */
    val onEmeraldSoft: Color = Mint100,
    /** Accent text on emerald (the amount's decimals, the comparison line). */
    val onEmeraldAccent: Color = Mint200,
    val wordmark: Color = EmeraldDeep,
    val wordmarkDot: Color = EmeraldDot,
    val navMuted: Color = NavMuted,
    /** Budget "near the limit": amber, always shown with an icon and words. */
    val warning: Color = Color(0xFF9A6700),
)

private val LightHero = Brush.linearGradient(listOf(HeroTop, Color(0xFF175E35), Color(0xFF124B2A)))
private val DarkHero = Brush.linearGradient(listOf(Color(0xFF1C6B3E), Color(0xFF144F2D), Color(0xFF0D3620)))
private val EmeraldButton = Brush.linearGradient(listOf(Color(0xFF23854D), Color(0xFF166437)))

private val LightClay = ClayPalette(shadow = ClayShadowLight, highlight = ClayHighlightLight)
private val DarkClay = ClayPalette(
    shadow = ClayShadowDark,
    highlight = ClayHighlightDark,
    hero = DarkHero,
    heroShadow = ClayShadowDark,
    wordmark = Green80,
    navMuted = Color(0xFFB8CCBB),
    warning = Color(0xFFF2C14E),
)

val LocalClayPalette = staticCompositionLocalOf { LightClay }

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
    val clayPalette = if (darkTheme) DarkClay else LightClay

    CompositionLocalProvider(LocalClayPalette provides clayPalette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = ClayShapes,
            content = content
        )
    }
}

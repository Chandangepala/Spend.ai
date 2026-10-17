package com.basic.spendai.ui.motion

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * True when "Remove animations" is on (Settings ▸ Accessibility). Decorative motion (count-up,
 * staggered rows, growing charts) is skipped; content simply appears.
 */
val LocalReducedMotion = compositionLocalOf { false }

fun reducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Material's "emphasized" feel for things growing into place. */
object SpendaiMotion {
    const val GROW_MS = 600
    const val COUNT_MS = 700
    const val STAGGER_MS = 35L
    const val STAGGER_MAX_ITEMS = 8
}

/**
 * Animates from 0 to [target] the first time it's shown, then between values when [target] changes.
 * Done on a 0→1 progress with the math in Double, so large totals keep their paise exactly.
 */
@Composable
fun animatedAmount(target: Double): Double {
    if (LocalReducedMotion.current) return target
    val from = remember { doubleArrayOf(0.0, target) } // [start, end] of the current run
    val progress = remember { Animatable(0f) }
    LaunchedEffect(target) {
        from[0] = from[0] + (from[1] - from[0]) * progress.value
        from[1] = target
        progress.snapTo(0f)
        progress.animateTo(1f, tween(SpendaiMotion.COUNT_MS, easing = FastOutSlowInEasing))
    }
    return from[0] + (from[1] - from[0]) * progress.value
}

/** A 0→1 fraction that grows into place once (charts, category bars); instant with reduced motion. */
@Composable
fun growFraction(target: Float, delayMs: Int = 0): Float {
    if (LocalReducedMotion.current) return target
    val grow = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        grow.animateTo(1f, tween(SpendaiMotion.GROW_MS, easing = FastOutSlowInEasing))
    }
    return target * grow.value
}

/** A soft spring "press-down" for tappable cards, driven by their own interaction source. */
@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    if (LocalReducedMotion.current) return this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Rows fade and slide up one after another the first time a list appears (first
 * [SpendaiMotion.STAGGER_MAX_ITEMS] only); rows added later, or scrolled back into view, just appear.
 */
@Composable
fun Modifier.staggeredEntrance(index: Int, enabled: Boolean): Modifier {
    if (!enabled || index >= SpendaiMotion.STAGGER_MAX_ITEMS || LocalReducedMotion.current) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * SpendaiMotion.STAGGER_MS)
        progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
    }
    return graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/** Whether the bottom bar is showing; scrolling down hides it, and the Add button shrinks to "+". */
val LocalChromeVisible = compositionLocalOf { true }

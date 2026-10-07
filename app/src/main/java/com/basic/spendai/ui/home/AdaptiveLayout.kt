package com.basic.spendai.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.window.core.layout.WindowSizeClass

enum class HomeLayout {
    /** Phone portrait or the folded outer screen. */
    SinglePane,

    /** Unfolded inner screen, book posture, landscape or tablet. */
    TwoPane,

    /** Half-open with a horizontal hinge, resting on a table. */
    Tabletop,
}

@Immutable
data class HomeLayoutInfo(val layout: HomeLayout, val hinge: HingeInfo? = null)

@Composable
fun rememberHomeLayoutInfo(): HomeLayoutInfo {
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val posture = adaptiveInfo.windowPosture
    val separatingHinges = posture.hingeList.filter { it.isSeparating }

    return when {
        posture.isTabletop -> HomeLayoutInfo(
            HomeLayout.Tabletop,
            separatingHinges.firstOrNull { !it.isVertical },
        )

        adaptiveInfo.windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) ->
            HomeLayoutInfo(HomeLayout.TwoPane, separatingHinges.firstOrNull { it.isVertical })

        else -> HomeLayoutInfo(HomeLayout.SinglePane)
    }
}

/**
 * Side-by-side panes. With a separating vertical [hinge] the split lands exactly on the fold
 * so no content sits under it; otherwise the space is split by [startWeight].
 */
@Composable
fun HingeAwareRow(
    hinge: HingeInfo?,
    modifier: Modifier = Modifier,
    startWeight: Float = 0.4f,
    start: @Composable () -> Unit,
    end: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Row(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        if (hinge != null) {
            val startWidth = with(density) { (hinge.bounds.left - origin.x).coerceAtLeast(0f).toDp() }
            val gap = with(density) { hinge.bounds.width.toDp() }
            Box(Modifier.width(startWidth).fillMaxHeight()) { start() }
            Spacer(Modifier.width(gap))
            Box(Modifier.weight(1f).fillMaxHeight()) { end() }
        } else {
            Box(Modifier.weight(startWeight).fillMaxHeight()) { start() }
            Box(Modifier.weight(1f - startWeight).fillMaxHeight()) { end() }
        }
    }
}

/** Stacked panes split at a horizontal [hinge] (tabletop), or in half when there is none. */
@Composable
fun HingeAwareColumn(
    hinge: HingeInfo?,
    modifier: Modifier = Modifier,
    top: @Composable () -> Unit,
    bottom: @Composable () -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current

    Column(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        if (hinge != null) {
            val topHeight = with(density) { (hinge.bounds.top - origin.y).coerceAtLeast(0f).toDp() }
            val gap = with(density) { hinge.bounds.height.toDp() }
            Box(Modifier.fillMaxWidth().height(topHeight)) { top() }
            Spacer(Modifier.height(gap))
            Box(Modifier.fillMaxWidth().weight(1f)) { bottom() }
        } else {
            Box(Modifier.fillMaxWidth().weight(1f)) { top() }
            Box(Modifier.fillMaxWidth().weight(1f)) { bottom() }
        }
    }
}

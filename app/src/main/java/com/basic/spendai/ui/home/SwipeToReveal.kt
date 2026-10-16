package com.basic.spendai.ui.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class RevealState { Closed, Open }

/** Width of the Delete action revealed behind a row. */
private val ActionWidth = 96.dp

/**
 * Swipe left to *reveal* a Delete button; nothing is deleted until it's tapped. Replaces
 * swipe-to-dismiss, which deleted on a 56dp drag or a quick flick in either direction.
 *
 * [open] says whether this row should be open (only one row is open at a time); [onOpenChange]
 * reports when the user opens or closes it. Tapping an open row closes it instead of [onClick].
 */
@Composable
fun SwipeToRevealDelete(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (onClickOverride: (() -> Unit)?) -> Unit,
) {
    val density = LocalDensity.current
    val actionPx = with(density) { ActionWidth.toPx() }
    val state = remember(actionPx) {
        AnchoredDraggableState(
            initialValue = RevealState.Closed,
            anchors = DraggableAnchors {
                RevealState.Closed at 0f
                RevealState.Open at -actionPx
            },
        )
    }
    val scope = rememberCoroutineScope()

    // Follow the parent: close when another row opens (or after a delete/undo).
    LaunchedEffect(open) {
        val target = if (open) RevealState.Open else RevealState.Closed
        if (state.settledValue != target) state.animateTo(target)
    }
    // Report swipes the user made.
    LaunchedEffect(state.settledValue) {
        onOpenChange(state.settledValue == RevealState.Open)
    }

    val offset = state.offset.takeIf { !it.isNaN() } ?: 0f
    Box(
        modifier
            .height(IntrinsicSize.Min)
            .semantics {
                // Swiping isn't the only way: TalkBack/Switch Access users get "Delete" as an action.
                customActions = listOf(CustomAccessibilityAction("Delete") { onDelete(); true })
            },
    ) {
        // The action sits behind the row and only exists while the row is (partly) open.
        if (offset < -1f) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(ActionWidth)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.error)
                    .clickable(onClickLabel = "Delete") { onDelete() }
                    .semantics { role = Role.Button },
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onError)
                Text("Delete", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onError)
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offset.roundToInt(), 0) }
                .anchoredDraggable(
                    state = state,
                    orientation = Orientation.Horizontal,
                    flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                        state = state,
                        // Opening needs a drag past half the action's width; a flick alone isn't enough.
                        positionalThreshold = { distance -> distance * 0.5f },
                        animationSpec = tween(200),
                    ),
                ),
        ) {
            content(
                if (state.settledValue == RevealState.Open) {
                    { scope.launch { state.animateTo(RevealState.Closed) } }
                } else {
                    null
                }
            )
        }
    }
}

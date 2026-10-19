package com.basic.spendai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.basic.spendai.ui.motion.pressScale
import com.basic.spendai.ui.theme.LocalClayPalette

/** A tab's title on the left and the clay settings cog on the right (Activity, Insights, Ask). */
@Composable
fun TabHeader(title: String, onOpenSettings: (() -> Unit)?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 64.dp)
            .padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        onOpenSettings?.let { ClayCircleButton(Icons.Rounded.Settings, contentDescription = "Settings", onClick = it) }
    }
}

/** The secondary action next to a [ClayButton]: a white clay pill with emerald text. */
@Composable
fun ClayOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
        Row(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .alpha(if (enabled) 1f else DisabledAlpha)
                .pressScale(interaction)
                .clay(MaterialTheme.colorScheme.surface, ClayTokens.PillRadius, elevation = 6.dp)
                .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            content = content,
        )
    }
}

/**
 * A filter or choice chip. Unselected it's a white clay pill (its icon in [iconTint], e.g. the
 * category colour); selected it turns emerald with white text, so the choice never rests on colour
 * alone, and TalkBack announces it as selected.
 */
@Composable
fun ClayChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
) {
    val interaction = remember { MutableInteractionSource() }
    val palette = LocalClayPalette.current
    val content = if (selected) palette.onEmerald else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressScale(interaction)
            .clay(
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                cornerRadius = ClayTokens.PillRadius,
                elevation = if (selected) 6.dp else ClayTokens.LowElevation,
                shadowColor = if (selected) palette.heroShadow.copy(alpha = 0.5f) else null,
                brush = if (selected) palette.button else null,
            )
            .toggleable(value = selected, interactionSource = interaction, indication = ripple(), role = Role.Checkbox, onValueChange = { onClick() })
            .padding(start = if (icon != null) 10.dp else 14.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon?.let {
            Icon(it, contentDescription = null, tint = if (selected) content else iconTint.takeOrElse(MaterialTheme.colorScheme.primary), modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

private fun Color.takeOrElse(other: Color) = if (this == Color.Unspecified) other else this

/**
 * A two-or-more-way switch: a track pressed into the ground, with the chosen option as a raised
 * white clay pill (Activity's Expenses / To review).
 */
@Composable
fun ClaySegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .background(Brush.verticalGradient(listOf(LocalClayPalette.current.shadow.copy(alpha = 0.35f), Color.Transparent)))
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { i, label ->
            val selected = i == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .then(
                        if (selected) {
                            Modifier.clay(MaterialTheme.colorScheme.surface, ClayTokens.PillRadius, elevation = ClayTokens.LowElevation)
                        } else {
                            Modifier.clip(CircleShape)
                        }
                    )
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(i) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else LocalClayPalette.current.navMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

/** A progress bar pressed into the card, with a raised, rounded fill ([fraction] 0..1). */
@Composable
fun ClayProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.10f), Color.Transparent))),
    ) {
        if (fraction > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)))
            )
        }
    }
}

/** Text fields sit in a white clay well: no outline until focused. Pair with [Modifier.clayField]. */
val ClayFieldShape = RoundedCornerShape(ClayTokens.CardRadius)

@Composable
fun clayFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color.Transparent,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedContainerColor = Color.Transparent,
    focusedContainerColor = Color.Transparent,
)

/** The clay well behind an OutlinedTextField using [ClayFieldShape] and [clayFieldColors]. */
@Composable
fun Modifier.clayField(): Modifier =
    clay(MaterialTheme.colorScheme.surface, ClayTokens.CardRadius, elevation = ClayTokens.LowElevation)

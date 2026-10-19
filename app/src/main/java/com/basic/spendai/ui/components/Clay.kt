package com.basic.spendai.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.ui.motion.pressScale
import com.basic.spendai.ui.theme.LocalClayPalette

/** Material's disabled look: the whole control at reduced opacity. */
internal const val DisabledAlpha = 0.45f

/** The few corner and depth sizes every clay surface picks from. */
object ClayTokens {
    /** Category and icon tiles. */
    val TileRadius = 20.dp
    /** Cards and list rows. */
    val CardRadius = 26.dp
    /** The emerald hero card. */
    val HeroRadius = 32.dp
    /** Fully rounded ends (clamped to half the height). */
    val PillRadius = 100.dp

    val LowElevation = 4.dp
    val CardElevation = 8.dp
    val RaisedElevation = 12.dp
}

/**
 * Claymorphism surface: a soft tinted drop shadow bottom-right, a light lift top-left,
 * a diagonal sheen to fake inner highlight/shade, and a thin translucent rim. [brush] fills it
 * with a gradient instead of [color] (the emerald surfaces); [color] then only tints the shadows.
 */
fun Modifier.clay(
    color: Color,
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 10.dp,
    shadowColor: Color? = null,
    brush: Brush? = null,
): Modifier = composed {
    val palette = LocalClayPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val dropColor = shadowColor ?: palette.shadow
    val liftColor = palette.highlight

    val shadowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            val radius = cornerRadius.toPx().coerceAtMost(size.minDimension / 2)
            val blur = elevation.toPx() * 1.6f
            val offset = elevation.toPx() * 0.6f
            drawIntoCanvas { canvas ->
                val paint = Paint()
                val framework = paint.asFrameworkPaint()
                framework.color = color.toArgb()
                framework.setShadowLayer(blur, -offset * 0.6f, -offset * 0.6f, liftColor.copy(alpha = 0.9f).toArgb())
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, framework)
                framework.setShadowLayer(blur, offset, offset, dropColor.copy(alpha = dropColor.alpha * 0.75f).toArgb())
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, framework)
            }
        }
    } else {
        shadow(elevation / 2, shape, clip = false, ambientColor = dropColor, spotColor = dropColor)
    }

    // On dark surfaces a white sheen and rim read as glowing glass, so they're much fainter there;
    // on emerald gradients a strong sheen washed out one end of wide buttons.
    val light = brush != null || color.luminance() > 0.5f
    shadowed
        .clip(shape)
        .then(if (brush != null) Modifier.background(brush) else Modifier.background(color))
        .background(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (brush != null) 0.14f else if (light) 0.35f else 0.06f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.08f),
            )
        )
        .border(1.dp, Color.White.copy(alpha = if (brush != null) 0.22f else if (light) 0.6f else 0.08f), shape)
}

@Composable
fun ClayCard(
    color: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = ClayTokens.CardRadius,
    elevation: Dp = ClayTokens.CardElevation,
    shadowColor: Color? = null,
    brush: Brush? = null,
    /** Text and icon colour inside; by default the scheme's "on" colour for [color]. */
    contentColor: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    // Like Material's Surface: text inside takes the matching "on" colour for this background.
    // Without it, untinted text stayed black and vanished on dark-mode cards.
    val onColor = contentColor
        .takeOrElse { MaterialTheme.colorScheme.contentColorFor(color) }
        .takeOrElse { MaterialTheme.colorScheme.onSurface }
    CompositionLocalProvider(LocalContentColor provides onColor) {
        Box(
            modifier = modifier.clay(color, cornerRadius, elevation, shadowColor, brush),
            content = content,
        )
    }
}

/**
 * A small borderless white clay pill (shortcut, suggestion). Visually ~36dp tall; its touch area
 * is still at least 48dp.
 */
@Composable
fun ClayPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClickLabel: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .pressScale(interaction)
            .clay(MaterialTheme.colorScheme.surface, ClayTokens.PillRadius, elevation = 6.dp)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            .padding(start = if (leading != null) 10.dp else 14.dp, end = if (trailing != null) 8.dp else 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        leading?.invoke()
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        trailing?.invoke()
    }
}

/** A round white clay icon button (the settings cog); 40dp to look at, 48dp to touch. */
@Composable
fun ClayCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .pressScale(interaction)
            .clay(MaterialTheme.colorScheme.surface, ClayTokens.PillRadius, elevation = 6.dp)
            .clickable(interactionSource = interaction, indication = ripple(), role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
    }
}

/** The primary action: an emerald gradient clay pill with white text. */
@Composable
fun ClayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val palette = LocalClayPalette.current
    CompositionLocalProvider(LocalContentColor provides palette.onEmerald) {
        Row(
            modifier = modifier
                .minimumInteractiveComponentSize()
                .alpha(if (enabled) 1f else DisabledAlpha)
                .pressScale(interaction)
                .clay(
                    color = MaterialTheme.colorScheme.primary,
                    cornerRadius = ClayTokens.PillRadius,
                    elevation = ClayTokens.RaisedElevation,
                    shadowColor = palette.heroShadow.copy(alpha = 0.6f),
                    brush = palette.button,
                )
                .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            content = content,
        )
    }
}

/**
 * A category's icon on a pastel clay tile (rounded square). The icon is a shade darker than the
 * category colour on light tiles so it keeps 3:1 against the pastel.
 */
@Composable
fun ClayIconTile(
    category: Category,
    modifier: Modifier = Modifier,
    icon: ImageVector = category.icon,
    size: Dp = 48.dp,
    contentDescription: String? = category.label,
) {
    val surface = MaterialTheme.colorScheme.surface
    val light = surface.luminance() > 0.5f
    val tile = Brush.linearGradient(
        listOf(lerp(surface, category.color, if (light) 0.20f else 0.30f), lerp(surface, category.color, if (light) 0.07f else 0.15f)),
        start = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY),
        end = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f),
    )
    Box(
        modifier = modifier
            .size(size)
            .clay(
                color = surface,
                cornerRadius = ClayTokens.TileRadius * (size / 48.dp),
                elevation = ClayTokens.LowElevation,
                shadowColor = category.color.copy(alpha = 0.35f),
                brush = tile,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (light) lerp(category.color, Color.Black, 0.18f) else lerp(category.color, Color.White, 0.35f),
            modifier = Modifier.size(size / 2),
        )
    }
}

/** One coloured piece of a [ClaySegmentBar]. */
data class BarSegment(val weight: Float, val color: Color)

/**
 * A pill-shaped track pressed into the surface, with raised, rounded segments and small gaps
 * between them (the hero card's category strip).
 */
@Composable
fun ClaySegmentBar(segments: List<BarSegment>, modifier: Modifier = Modifier, track: Color = Color(0x730E2E1A)) {
    Row(
        modifier = modifier
            .height(12.dp)
            .clip(CircleShape)
            .background(track)
            // Inset look: the track is shaded along its top edge.
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent)))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.filter { it.weight > 0f }.forEach { segment ->
            Box(
                Modifier
                    .weight(segment.weight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(segment.color)
                    // Raised look: a highlight along the top of each segment.
                    .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.Transparent)))
            )
        }
    }
}

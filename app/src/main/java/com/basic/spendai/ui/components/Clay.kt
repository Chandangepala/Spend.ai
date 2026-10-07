package com.basic.spendai.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.basic.spendai.ui.theme.LocalClayPalette

/**
 * Claymorphism surface: a soft tinted drop shadow bottom-right, a light lift top-left,
 * a diagonal sheen to fake inner highlight/shade, and a thin translucent rim.
 */
fun Modifier.clay(
    color: Color,
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 10.dp,
    shadowColor: Color? = null,
): Modifier = composed {
    val palette = LocalClayPalette.current
    val shape = RoundedCornerShape(cornerRadius)
    val dropColor = shadowColor ?: palette.shadow
    val liftColor = palette.highlight

    val shadowed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind {
            val radius = cornerRadius.toPx()
            val blur = elevation.toPx() * 1.6f
            val offset = elevation.toPx() * 0.6f
            drawIntoCanvas { canvas ->
                val paint = Paint()
                val framework = paint.asFrameworkPaint()
                framework.color = color.toArgb()
                framework.setShadowLayer(blur, -offset * 0.6f, -offset * 0.6f, liftColor.copy(alpha = 0.9f).toArgb())
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, framework)
                framework.setShadowLayer(blur, offset, offset, dropColor.copy(alpha = 0.75f).toArgb())
                canvas.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, radius, radius, framework)
            }
        }
    } else {
        shadow(elevation / 2, shape, clip = false, ambientColor = dropColor, spotColor = dropColor)
    }

    shadowed
        .clip(shape)
        .background(color)
        .background(
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.35f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.10f),
            )
        )
        .border(1.5.dp, Color.White.copy(alpha = 0.45f), shape)
}

@Composable
fun ClayCard(
    color: Color,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 10.dp,
    shadowColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.clay(color, cornerRadius, elevation, shadowColor),
        content = content,
    )
}

package com.basic.spendai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.basic.spendai.ui.components.BarSegment
import com.basic.spendai.ui.components.ClaySegmentBar
import com.basic.spendai.ui.components.ClayTokens
import com.basic.spendai.ui.theme.LocalClayPalette
import java.text.DecimalFormatSymbols
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.motion.animatedAmount
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TotalSpentCard(
    total: Double,
    expenses: List<Expense>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
    label: String = "Total Spent",
    /** e.g. "₹1,200 less than this time last month"; shown under the count. */
    comparison: String? = null,
) {
    val palette = LocalClayPalette.current
    val shares = remember(expenses) { categoryTotals(expenses) }
    val moreColor = Color(0xFFB2D6C0).copy(alpha = 0.7f)

    ClayCard(
        color = MaterialTheme.colorScheme.primary,
        brush = palette.hero,
        contentColor = palette.onEmerald,
        shadowColor = palette.heroShadow.copy(alpha = 0.55f),
        modifier = modifier.fillMaxWidth(),
        cornerRadius = ClayTokens.HeroRadius,
        elevation = 14.dp,
    ) {
        // A soft shine in the top-left corner, as if lit from there.
        Box(
            Modifier
                .matchParentSize()
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.13f), Color.Transparent),
                            center = Offset(size.width * 0.08f, 0f),
                            radius = size.minDimension * 0.7f,
                        ),
                        radius = size.minDimension * 0.7f,
                        center = Offset(size.width * 0.08f, 0f),
                    )
                }
        )
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = palette.onEmeraldSoft, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, color = palette.onEmeraldSoft)
            }
            Spacer(Modifier.height(12.dp))
            // One Text, so screen readers and tests still see the whole amount; only the decimals look smaller.
            Text(
                smallDecimals(
                    formatCurrency(animatedAmount(total), LocalCurrency.current),
                    SpanStyle(fontSize = 0.72.em, fontWeight = FontWeight.SemiBold, color = palette.onEmeraldAccent),
                ),
                style = MaterialTheme.typography.displaySmall,
                color = palette.onEmerald,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = when (expenses.size) {
                    1 -> "1 transaction"
                    else -> "${expenses.size} transactions"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = palette.onEmeraldSoft,
            )
            comparison?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = palette.onEmeraldAccent)
            }

            if (shares.isNotEmpty()) {
                val segments = stripSegments(shares)
                Spacer(Modifier.height(20.dp))
                ClaySegmentBar(
                    segments.map { BarSegment(it.amount.toFloat(), it.category?.color ?: moreColor) },
                    Modifier.fillMaxWidth(),
                )
                if (showLegend) {
                    Spacer(Modifier.height(14.dp))
                    // Wraps: labels like "Dining & Drinks" don't all fit on one line on a phone.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        segments.forEach { segment ->
                            if (segment.category != null) {
                                LegendDot(segment.label, segment.category.color, palette.onEmeraldSoft)
                            } else {
                                LegendDot("+ ${segment.label}", moreColor, palette.onEmeraldAccent)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * [amount] with its decimal part ("₹25,778.78" → ".78") drawn in [decimals]; unchanged when the
 * currency has no minor unit.
 */
internal fun smallDecimals(amount: String, decimals: SpanStyle): AnnotatedString {
    val separator = DecimalFormatSymbols.getInstance().monetaryDecimalSeparator
    val start = amount.lastIndexOf(separator)
    if (start < 0) return AnnotatedString(amount)
    var end = start + 1
    while (end < amount.length && amount[end].isDigit()) end++
    if (end == start + 1) return AnnotatedString(amount)
    return buildAnnotatedString {
        append(amount)
        addStyle(decimals, start, end)
    }
}

/** One piece of the strip: a named category, or (category = null) everything past the top few. */
data class StripSegment(val category: Category?, val amount: Double, val label: String)

/**
 * Top [keep] categories as their own segments, the rest folded into one neutral "N more" segment,
 * so every colour in the strip has a legend entry (identity is never colour alone).
 */
fun stripSegments(shares: List<Pair<Category, Double>>, keep: Int = 3): List<StripSegment> {
    val top = shares.take(keep).map { (c, a) -> StripSegment(c, a, c.label) }
    val rest = shares.drop(keep)
    return if (rest.isEmpty()) top else top + StripSegment(null, rest.sumOf { it.second }, "${rest.size} more")
}

@Composable
private fun LegendDot(label: String, dotColor: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEBF4ED)
@Composable
private fun TotalSpentCardPreview() {
    SpendaiTheme {
        TotalSpentCard(
            total = 1730.0,
            expenses = listOf(
                Expense(title = "Lunch", amount = 450.0, category = Category.Food),
                Expense(title = "Cab", amount = 280.0, category = Category.Transport),
                Expense(title = "Shoes", amount = 1000.0, category = Category.Shopping),
            ),
            modifier = Modifier.padding(24.dp),
        )
    }
}

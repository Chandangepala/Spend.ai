package com.basic.spendai.ui.insights

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.insights.MonthTotal
import com.basic.spendai.insights.niceCeiling
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import java.time.format.TextStyle
import java.util.Locale

private val ChartHeight = 148.dp
private val MaxBarWidth = 24.dp

/**
 * Spending per month as columns (one series: the app's primary green, no legend). Tap a month to
 * read its exact amount above the chart; the current month is selected by default and marked
 * "so far". "View as list" shows the same numbers as text.
 */
@Composable
fun MonthlyTrendCard(months: List<MonthTotal>, modifier: Modifier = Modifier) {
    if (months.isEmpty()) return
    val currency = LocalCurrency.current
    var selected by rememberSaveable { mutableIntStateOf(months.lastIndex) }
    var asList by rememberSaveable { mutableStateOf(false) }
    val pick = months[selected.coerceIn(months.indices)]
    val axisMax = niceCeiling(months.maxOf { it.total })
    val colors = MaterialTheme.colorScheme

    ClayCard(color = colors.surface, modifier = modifier.fillMaxWidth(), cornerRadius = 28.dp, elevation = 10.dp) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last ${months.size} months", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { asList = !asList }) { Text(if (asList) "View as chart" else "View as list") }
            }
            if (asList) {
                months.asReversed().forEach { m ->
                    Row(Modifier.padding(vertical = 6.dp)) {
                        Text(monthName(m, long = true), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatCurrency(m.total, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                }
                return@Column
            }

            // The selected month's value, large: the tap-to-read "tooltip" for a touch screen.
            Text(monthName(pick, long = true), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            Text(formatCurrency(pick.total, currency), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            // The scale label sits above the plot, never inside it where a tall bar would run into it.
            Text(
                compactMoney(axisMax, currency),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(bottom = 2.dp),
            )

            Box(Modifier.fillMaxWidth().height(ChartHeight)) {
                // Recessive scale: one hairline at a round maximum, one at half, plus the baseline.
                listOf(1f, 0.5f).forEach { f ->
                    Box(Modifier.fillMaxSize().padding(bottom = ChartHeight * f), contentAlignment = Alignment.BottomStart) {
                        HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
                    }
                }
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
                    months.forEachIndexed { i, m ->
                        val isSelected = i == selected
                        BoxWithConstraints(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                // The whole column is the touch target, not just the (maybe tiny) bar.
                                .selectable(selected = isSelected, role = Role.Tab, onClick = { selected = i })
                                .semantics { contentDescription = "${monthName(m, long = true)}: ${formatCurrency(m.total, currency)}" },
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            val fraction = (m.total / axisMax).toFloat().coerceIn(0f, 1f)
                            Box(
                                Modifier
                                    .width(min(MaxBarWidth, maxWidth * 0.5f))
                                    // Even a ₹0 month shows a sliver so the column reads as "nothing", not "missing".
                                    .fillMaxHeight(fraction.coerceAtLeast(0.012f))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(if (isSelected) colors.primary else colors.primary.copy(alpha = 0.38f)),
                            )
                        }
                    }
                }
                HorizontalDivider(Modifier.align(Alignment.BottomStart), thickness = 1.dp, color = colors.outline)
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                months.forEachIndexed { i, m ->
                    Text(
                        monthName(m, long = false),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (i == selected) colors.onSurface else colors.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** "October 2026 · so far" / "Oct". */
private fun monthName(m: MonthTotal, long: Boolean): String {
    val name = m.month.month.getDisplayName(if (long) TextStyle.FULL else TextStyle.SHORT, Locale.getDefault())
    return if (long) "$name ${m.month.year}${if (m.partial) " · so far" else ""}" else name
}

/** Axis ticks: "₹10K", "₹2.5K", "₹1.2L"-free plain K/M so they stay short. */
private fun compactMoney(v: Double, currency: AppCurrency): String {
    val symbol = currency.symbol
    return when {
        v >= 1_000_000 -> "$symbol${trim(v / 1_000_000)}M"
        v >= 1_000 -> "$symbol${trim(v / 1_000)}K"
        else -> "$symbol${trim(v)}"
    }
}

private fun trim(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v)

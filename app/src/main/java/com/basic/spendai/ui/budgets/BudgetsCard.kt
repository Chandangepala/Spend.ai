package com.basic.spendai.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.basic.spendai.budgets.BudgetLevel
import com.basic.spendai.budgets.BudgetProgress
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.ClayIconTile
import com.basic.spendai.ui.components.ClayProgressBar
import com.basic.spendai.ui.theme.LocalClayPalette
import com.basic.spendai.ui.motion.growFraction
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency

/** Status colours are reserved for budget state and always come with an icon and words. */
@Composable
internal fun statusColor(level: BudgetLevel): Color {
    return when (level) {
        BudgetLevel.OK -> MaterialTheme.colorScheme.primary
        BudgetLevel.NEAR -> LocalClayPalette.current.warning
        BudgetLevel.OVER -> MaterialTheme.colorScheme.error
    }
}

internal fun statusIcon(level: BudgetLevel): ImageVector = when (level) {
    BudgetLevel.OK -> Icons.Rounded.CheckCircle
    BudgetLevel.NEAR -> Icons.Rounded.Warning
    BudgetLevel.OVER -> Icons.Rounded.Error
}

/** "85% used · ₹900.00 left · 10 days to go" / "Over by ₹300.00 · 1 day to go". */
internal fun statusLine(p: BudgetProgress, money: (Double) -> String): String {
    val days = when (p.daysLeft) {
        0 -> "last day"
        1 -> "1 day to go"
        else -> "${p.daysLeft} days to go"
    }
    return when (p.level) {
        BudgetLevel.OVER -> "Over by ${money(-p.remaining)} · $days"
        else -> "${p.percent}% used · ${money(p.remaining)} left · $days"
    }
}

/** Each budget's month so far; tap a row to change or remove it. */
@Composable
fun BudgetsCard(budgets: List<BudgetProgress>, onAdd: () -> Unit, onEdit: (BudgetProgress) -> Unit, canAdd: Boolean, modifier: Modifier = Modifier) {
    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (canAdd) {
                    TextButton(onClick = onAdd) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Add")
                    }
                }
            }
            if (budgets.isEmpty()) {
                Text(
                    "Set a monthly limit for a category. SpendAI suggests one from your past months and nudges you at 80% and 100%.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            budgets.forEachIndexed { i, p -> BudgetRow(p, onClick = { onEdit(p) }, growDelayMs = i * 60) }
        }
    }
}

@Composable
private fun BudgetRow(p: BudgetProgress, onClick: () -> Unit, growDelayMs: Int) {
    val currency = LocalCurrency.current
    val money = { v: Double -> formatCurrency(v, currency) }
    val color = statusColor(p.level)
    val shown = growFraction(p.fraction.coerceIn(0.0, 1.0).toFloat(), growDelayMs)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit budget", onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClayIconTile(p.category, size = 36.dp, contentDescription = null)
            Spacer(Modifier.size(12.dp))
            Text(p.category.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(money(p.spent), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(
                " of ${money(p.limit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        ClayProgressBar(shown, color)
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(statusIcon(p.level), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Text(statusLine(p, money), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

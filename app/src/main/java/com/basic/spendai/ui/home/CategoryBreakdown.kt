package com.basic.spendai.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import kotlin.math.roundToInt

/** Total spent per category, largest first, omitting categories with nothing spent. */
fun categoryTotals(expenses: List<Expense>): List<Pair<Category, Double>> =
    expenses.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amount } }
        .filterValues { it > 0 }
        .toList()
        .sortedByDescending { it.second }

@Composable
fun CategoryBreakdown(expenses: List<Expense>, modifier: Modifier = Modifier) {
    val totals = remember(expenses) { categoryTotals(expenses) }
    val grandTotal = totals.sumOf { it.second }

    ClayCard(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        elevation = 10.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("By category", style = MaterialTheme.typography.titleMedium)
            if (totals.isEmpty()) {
                Text(
                    "Your spending by category will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            totals.forEach { (category, amount) ->
                CategoryRow(category, amount, fraction = (amount / grandTotal).toFloat())
            }
        }
    }
}

@Composable
private fun CategoryRow(category: Category, amount: Double, fraction: Float) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(category.color)
            )
            Spacer(Modifier.size(8.dp))
            Text(category.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                formatCurrency(amount, LocalCurrency.current),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = category.color,
            )
            Text(
                "${(fraction * 100).roundToInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(category.color.copy(alpha = 0.15f))
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(category.color)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEAF6EC)
@Composable
private fun CategoryBreakdownPreview() {
    SpendaiTheme {
        CategoryBreakdown(
            expenses = listOf(
                Expense(title = "Lunch", amount = 450.0, category = Category.Food),
                Expense(title = "Cab", amount = 280.0, category = Category.Transport),
                Expense(title = "Shoes", amount = 1000.0, category = Category.Shopping),
            ),
            modifier = Modifier.padding(24.dp),
        )
    }
}

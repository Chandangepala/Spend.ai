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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency

@Composable
fun TotalSpentCard(
    total: Double,
    expenses: List<Expense>,
    modifier: Modifier = Modifier,
    showLegend: Boolean = true,
) {
    val onCard = MaterialTheme.colorScheme.onPrimary
    val shares = remember(expenses) { categoryTotals(expenses) }

    ClayCard(
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 32.dp,
        elevation = 14.dp,
    ) {
        Column(Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(onCard.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, contentDescription = null, tint = onCard)
                }
                Spacer(Modifier.size(12.dp))
                Text("Total Spent", style = MaterialTheme.typography.titleMedium, color = onCard.copy(alpha = 0.9f))
            }
            Spacer(Modifier.height(16.dp))
            Text(formatCurrency(total, LocalCurrency.current), style = MaterialTheme.typography.displaySmall, color = onCard)
            Text(
                text = when (expenses.size) {
                    1 -> "1 transaction"
                    else -> "${expenses.size} transactions"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onCard.copy(alpha = 0.8f),
            )

            if (shares.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(onCard.copy(alpha = 0.2f)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    shares.forEach { (category, amount) ->
                        Box(
                            Modifier
                                .weight(amount.toFloat())
                                .height(10.dp)
                                .background(category.color)
                        )
                    }
                }
                if (showLegend) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        shares.take(3).forEach { (category, _) -> LegendDot(category, onCard) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(category: Category, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(category.color)
        )
        Spacer(Modifier.size(4.dp))
        Text(category.label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.9f))
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEAF6EC)
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

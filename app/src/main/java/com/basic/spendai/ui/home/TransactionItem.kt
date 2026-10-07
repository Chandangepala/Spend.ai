package com.basic.spendai.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.clay
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import com.basic.spendai.util.formatDate

@Composable
fun TransactionItem(expense: Expense, modifier: Modifier = Modifier) {
    val category = expense.category
    val surface = MaterialTheme.colorScheme.surface

    ClayCard(
        color = surface,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clay(
                        color = lerp(surface, category.color, 0.18f),
                        cornerRadius = 24.dp,
                        elevation = 4.dp,
                        shadowColor = category.color.copy(alpha = 0.5f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(category.icon, contentDescription = category.label, tint = category.color)
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    expense.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${category.label} • ${formatDate(expense.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "-${formatCurrency(expense.amount, LocalCurrency.current)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = category.color,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEAF6EC)
@Composable
private fun TransactionItemPreview() {
    SpendaiTheme {
        TransactionItem(
            Expense(title = "Metro card recharge", amount = 300.0, category = Category.Transport),
            modifier = Modifier.padding(20.dp),
        )
    }
}

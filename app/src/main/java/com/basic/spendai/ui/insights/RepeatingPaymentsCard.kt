package com.basic.spendai.ui.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.basic.spendai.insights.Finding
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency

/** Payments that repeat about monthly (subscriptions, gym, rent…). A list, not a chart: names matter. */
@Composable
fun RepeatingPaymentsCard(payments: Finding.Subscriptions?, modifier: Modifier = Modifier) {
    val currency = LocalCurrency.current
    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth(), cornerRadius = 28.dp, elevation = 10.dp) {
        Column(Modifier.padding(20.dp)) {
            Text("Repeating payments", style = MaterialTheme.typography.titleMedium)
            if (payments == null) {
                Text(
                    "None spotted yet. Payments to the same place for about the same amount each month show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                return@Column
            }
            payments.items.forEach { item ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(item.merchant, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${formatCurrency(item.amount, currency)}/mo", style = MaterialTheme.typography.bodyLarge)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("About", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(
                    "${formatCurrency(payments.monthlyTotal, currency)} a month",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

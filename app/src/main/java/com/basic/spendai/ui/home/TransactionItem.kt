package com.basic.spendai.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.ClayIconTile
import com.basic.spendai.ui.components.titleDecor
import com.basic.spendai.ui.theme.DisplayFamily
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.basic.spendai.ui.motion.pressScale
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import com.basic.spendai.util.formatDate

@Composable
fun TransactionItem(expense: Expense, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val interaction = remember { MutableInteractionSource() }
    val category = expense.category
    val decor = remember(expense.title, category) { titleDecor(expense.title, category) }

    ClayCard(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction),
    ) {
        Row(
            // Clickable inside the card, so the ripple follows its rounded shape.
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interactionSource = interaction, indication = ripple(), onClickLabel = "Edit", onClick = onClick)
                    } else {
                        Modifier
                    }
                )
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClayIconTile(category, icon = decor.icon)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        expense.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    decor.emoji?.let {
                        // Decoration only: TalkBack already reads the title and the category.
                        Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 6.dp).clearAndSetSemantics {})
                    }
                }
                Text(
                    "${category.label} • ${formatDate(expense.timestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                "-${formatCurrency(expense.amount, LocalCurrency.current)}",
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = DisplayFamily),
                // Text keeps the text colour (slate/orange amounts were unreadable in dark mode);
                // the tinted icon carries the category.
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFEBF4ED)
@Composable
private fun TransactionItemPreview() {
    SpendaiTheme {
        TransactionItem(
            Expense(title = "Metro card recharge", amount = 300.0, category = Category.Transport),
            modifier = Modifier.padding(20.dp),
        )
    }
}

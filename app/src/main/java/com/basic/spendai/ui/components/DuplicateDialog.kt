package com.basic.spendai.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.basic.spendai.data.Expense
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import com.basic.spendai.util.formatDate

/** Asked before saving a spend that matches an existing expense (same amount ±1, within a day). */
@Composable
fun DuplicateDialog(existing: Expense, onAddAnyway: () -> Unit, onSkip: () -> Unit) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Looks like a duplicate") },
        text = {
            Text(
                "\"${existing.title}\" for ${formatCurrency(existing.amount, LocalCurrency.current)} " +
                    "(${formatDate(existing.timestamp)}) is already in your expenses."
            )
        },
        confirmButton = { TextButton(onClick = onAddAnyway) { Text("Add anyway") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Skip") } },
    )
}

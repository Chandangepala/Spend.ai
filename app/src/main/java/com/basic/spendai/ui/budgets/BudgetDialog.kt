package com.basic.spendai.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.basic.spendai.ai.CoachWriter
import com.basic.spendai.budgets.BudgetProgress
import com.basic.spendai.data.Category
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import java.text.DecimalFormat

/** Picks the amount typed in a money field ("6,000" or "6000.50"); null unless it's above zero. */
internal fun parseMoney(text: String): Double? = text.replace(",", "").trim().toDoubleOrNull()?.takeIf { it > 0 }

/** A plain number for a text field: 6000.0 -> "6000", 99.5 -> "99.5". */
internal fun plainAmount(value: Double): String = DecimalFormat("0.##").format(value)

/**
 * Add or change one category's monthly budget. The suggestion (median of the last 3 months) is
 * computed by code; the sentence under it is the coach's wording of the same numbers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetDialog(
    editing: BudgetProgress?,
    /** Categories that can get a new budget, most-used first. */
    available: List<Category>,
    onSuggest: suspend (Category) -> CoachWriter.Topic.Suggestion?,
    onCoach: Coach,
    onSave: (Category, Double) -> Unit,
    onRemove: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    val currency = LocalCurrency.current
    var category by remember { mutableStateOf(editing?.category ?: available.first()) }
    var amount by remember { mutableStateOf(editing?.limit?.let(::plainAmount) ?: "") }
    var typed by remember { mutableStateOf(editing != null) }
    var suggestion by remember { mutableStateOf<CoachWriter.Topic.Suggestion?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(category) {
        loaded = false
        suggestion = onSuggest(category)
        loaded = true
        // Pre-fill the suggestion until the user types their own amount.
        if (!typed) amount = suggestion?.let { plainAmount(it.suggested) } ?: ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "${editing.category.label} budget" else "New budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (editing == null) {
                    ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                        OutlinedTextField(
                            value = category.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            leadingIcon = { Icon(category.icon, contentDescription = null, tint = category.color) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        )
                        ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            available.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.label) },
                                    leadingIcon = { Icon(c.icon, contentDescription = null, tint = c.color, modifier = Modifier.size(20.dp)) },
                                    onClick = {
                                        category = c
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        typed = true
                    },
                    label = { Text("Monthly limit") },
                    prefix = { Text(currency.javaCurrency.getSymbol()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val s = suggestion
                when {
                    s != null -> {
                        if (parseMoney(amount) != s.suggested) {
                            SuggestionChip(
                                onClick = {
                                    amount = plainAmount(s.suggested)
                                    typed = true
                                },
                                label = { Text("Use suggested ${formatCurrency(s.suggested, currency)}") },
                            )
                        }
                        CoachLine(s, onCoach)
                    }
                    loaded -> Text(
                        "Not enough history to suggest an amount yet: a suggestion needs two full months of spending.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    parseMoney(amount)?.let { onSave(category, it) }
                    onDismiss()
                },
                enabled = parseMoney(amount) != null,
            ) { Text("Save") }
        },
        dismissButton = {
            if (editing != null) {
                TextButton(onClick = {
                    onRemove(editing.category)
                    onDismiss()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

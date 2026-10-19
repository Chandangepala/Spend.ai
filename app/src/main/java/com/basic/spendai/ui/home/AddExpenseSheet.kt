package com.basic.spendai.ui.home

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.basic.spendai.ui.components.ClayButton
import com.basic.spendai.ui.components.ClayOutlinedButton
import com.basic.spendai.ui.components.ClayFieldShape
import com.basic.spendai.ui.components.clayField
import com.basic.spendai.ui.components.clayFieldColors
import com.basic.spendai.ai.AiResult
import com.basic.spendai.ai.QuickEntryItem
import com.basic.spendai.ai.ReceiptResult
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatDay
import com.basic.spendai.util.toPickerDate
import com.basic.spendai.util.withPickedDate
import java.math.BigDecimal
import java.text.DecimalFormat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, category: Category) -> Unit,
    /** Splits a note like "200 chai, 450 auto"; null hides quick entry. */
    onQuickParse: (suspend (String) -> AiResult<List<QuickEntryItem>>)? = null,
    onSaveMany: (List<QuickEntryItem>) -> Unit = {},
    /** Reads a receipt photo; null result means no text was found. Null hides receipt scanning. */
    onScanReceipt: (suspend (Uri) -> AiResult<ReceiptResult>?)? = null,
    /** Non-null opens the sheet in edit mode for this expense; [onUpdate] receives the changed copy. */
    editing: Expense? = null,
    onUpdate: (Expense) -> Unit = {},
    /** Categories most-used first; the first 7 show as chips before "+ More". */
    categoryOrder: List<Category> = orderCategories(emptyMap()),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf(editing?.title.orEmpty()) }
    var amountText by rememberSaveable {
        mutableStateOf(editing?.amount?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty())
    }
    // New expenses start on the user's most-used category.
    var category by rememberSaveable { mutableStateOf(editing?.category ?: categoryOrder.first()) }
    var timestamp by rememberSaveable { mutableLongStateOf(editing?.timestamp ?: 0L) }
    var pickingDate by remember { mutableStateOf(false) }
    var showErrors by remember { mutableStateOf(false) }

    var quickText by rememberSaveable { mutableStateOf("") }
    var parsing by remember { mutableStateOf(false) }
    var quickError by remember { mutableStateOf<String?>(null) }
    var reviewFromAi by remember { mutableStateOf(false) }
    val reviewRows = remember { mutableStateListOf<QuickRow>() }
    val amountFormat = remember { DecimalFormat("0.##") }

    var scanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var receipt by remember { mutableStateOf<ReceiptResult?>(null) }
    var receiptAsItems by remember { mutableStateOf(false) }

    fun showReceipt(scanned: ReceiptResult, asItems: Boolean) {
        receiptAsItems = asItems
        reviewRows.clear()
        reviewRows += if (asItems) {
            scanned.items.map { QuickRow(it.title, amountFormat.format(it.amount), it.category) }
        } else {
            listOf(QuickRow(scanned.merchant ?: "Receipt", scanned.total?.let(amountFormat::format).orEmpty(), scanned.category))
        }
    }

    val amount = amountText.replace(',', '.').toDoubleOrNull()
    val amountValid = amount != null && amount > 0
    val titleValid = title.isNotBlank()

    fun close() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(if (editing != null) "Edit expense" else "Add expense", style = MaterialTheme.typography.titleLarge)

            if (reviewRows.isNotEmpty()) {
                receipt?.let { scanned ->
                    ReceiptHeader(scanned, receiptAsItems, onAsItemsChange = { showReceipt(scanned, it) })
                }
                QuickEntryReview(
                    rows = reviewRows,
                    fromAi = reviewFromAi,
                    onRowChange = { index, row -> reviewRows[index] = row },
                    onRemove = { index -> reviewRows.removeAt(index) },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ClayOutlinedButton(onClick = {
                        reviewRows.clear()
                        receipt = null
                    }) { Text("Back", style = MaterialTheme.typography.labelLarge) }
                    ClayButton(
                        onClick = {
                            onSaveMany(reviewRows.map { QuickEntryItem(it.title.trim(), it.amount!!, it.category) })
                            close()
                        },
                        enabled = reviewRows.all { it.valid },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (reviewRows.size == 1) "Add 1 expense" else "Add ${reviewRows.size} expenses", style = MaterialTheme.typography.labelLarge)
                    }
                }
                return@Column
            }

            if (onQuickParse != null && editing == null) {
                QuickEntryInput(
                    text = quickText,
                    onTextChange = {
                        quickText = it
                        quickError = null
                    },
                    parsing = parsing,
                    error = quickError,
                    onSplit = {
                        parsing = true
                        scope.launch {
                            val result = onQuickParse(quickText)
                            parsing = false
                            if (result.value.isEmpty()) {
                                quickError = "No amounts found. Try \"200 chai, 450 auto\"."
                            } else {
                                reviewFromAi = result.fromAi
                                receipt = null
                                reviewRows.clear()
                                reviewRows += result.value.map { QuickRow(it.title, amountFormat.format(it.amount), it.category) }
                            }
                        }
                    },
                )
            }

            if (onScanReceipt != null && editing == null) {
                ReceiptScanRow(
                    scanning = scanning,
                    error = scanError,
                    onPhoto = { uri ->
                        scanning = true
                        scanError = null
                        scope.launch {
                            val result = onScanReceipt(uri)
                            scanning = false
                            if (result == null) {
                                scanError = "Couldn't read any text. Try a sharper, well-lit photo."
                            } else {
                                reviewFromAi = result.fromAi
                                receipt = result.value
                                // Offer items first when the model split them and they add up.
                                showReceipt(result.value, asItems = result.value.items.isNotEmpty())
                            }
                        }
                    },
                )
            }

            if ((onQuickParse != null || onScanReceipt != null) && editing == null) {
                Text(
                    "or fill in one expense",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What did you spend on?") },
                singleLine = true,
                isError = showErrors && !titleValid,
                supportingText = if (showErrors && !titleValid) {
                    { Text("Enter a title") }
                } else null,
                shape = ClayFieldShape,
                colors = clayFieldColors(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clayField(),
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("""\d*[.,]?\d{0,2}"""))) amountText = input
                },
                label = { Text("Amount") },
                prefix = { Text(LocalCurrency.current.symbol) },
                singleLine = true,
                isError = showErrors && !amountValid,
                supportingText = if (showErrors && !amountValid) {
                    { Text("Enter an amount greater than 0") }
                } else null,
                shape = ClayFieldShape,
                colors = clayFieldColors(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clayField(),
            )

            if (editing != null) {
                ClayOutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = null)
                    Text("Date: ${formatDay(timestamp)}", style = MaterialTheme.typography.labelLarge)
                }
            }

            Text("Category", style = MaterialTheme.typography.titleMedium)
            CategoryChips(selected = category, onSelect = { category = it }, order = categoryOrder)

            Spacer(Modifier.height(4.dp))

            ClayButton(
                onClick = {
                    if (titleValid && amountValid) {
                        if (editing != null) {
                            onUpdate(editing.copy(title = title.trim(), amount = amount!!, category = category, timestamp = timestamp))
                        } else {
                            onSave(title, amount!!, category)
                        }
                        close()
                    } else {
                        showErrors = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (editing != null) "Save changes" else "Save expense", style = MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (pickingDate) {
        val today = remember { toPickerDate(System.currentTimeMillis()) }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = toPickerDate(timestamp),
            // Spends happen in the past; don't let a mistyped date land in the future.
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= today
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { timestamp = withPickedDate(timestamp, it) }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

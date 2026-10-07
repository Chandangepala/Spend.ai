package com.basic.spendai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.ui.components.clay
import com.basic.spendai.util.LocalCurrency
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddExpenseSheet(
    onDismiss: () -> Unit,
    onSave: (title: String, amount: Double, category: Category) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.Food) }
    var showErrors by remember { mutableStateOf(false) }

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
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Add expense", style = MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("What did you spend on?") },
                singleLine = true,
                isError = showErrors && !titleValid,
                supportingText = if (showErrors && !titleValid) {
                    { Text("Enter a title") }
                } else null,
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier.fillMaxWidth(),
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
                shape = RoundedCornerShape(20.dp),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Category", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Category.entries.forEach { option ->
                    FilterChip(
                        selected = option == category,
                        onClick = { category = option },
                        label = { Text(option.label) },
                        leadingIcon = {
                            Icon(
                                option.icon,
                                contentDescription = null,
                                tint = if (option == category) Color.White else option.color,
                            )
                        },
                        shape = RoundedCornerShape(16.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = option.color,
                            selectedLabelColor = Color.White,
                            labelColor = option.color,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = option == category,
                            borderColor = option.color.copy(alpha = 0.5f),
                        ),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = {
                    if (titleValid && amountValid) {
                        onSave(title, amount!!, category)
                        close()
                    } else {
                        showErrors = true
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                elevation = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clay(MaterialTheme.colorScheme.primary, cornerRadius = 24.dp, elevation = 8.dp),
            ) {
                Text("Save expense", color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

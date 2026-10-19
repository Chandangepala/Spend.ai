package com.basic.spendai.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.basic.spendai.ui.components.ClayFieldShape
import com.basic.spendai.ui.components.ClayOutlinedButton
import com.basic.spendai.ui.components.ClayPill
import com.basic.spendai.ui.components.clayField
import com.basic.spendai.ui.components.clayFieldColors
import com.basic.spendai.ui.components.AiBadge
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.util.LocalCurrency

/** One editable line in the quick-entry review list. */
data class QuickRow(val title: String, val amountText: String, val category: Category) {
    val amount: Double? get() = amountText.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    val valid: Boolean get() = title.isNotBlank() && amount != null
}

/** "200 chai, 450 auto" box with an optional mic, and a button that splits it into expenses. */
@Composable
fun QuickEntryInput(
    text: String,
    onTextChange: (String) -> Unit,
    parsing: Boolean,
    error: String?,
    onSplit: () -> Unit,
) {
    val context = LocalContext.current
    // Rupee users get Indian English, which hears amounts and words like "auto" or "samosa" better.
    val speechLanguage = if (LocalCurrency.current == AppCurrency.INR) "en-IN" else null
    val speechAvailable = remember { SpeechRecognizer.isRecognitionAvailable(context) }
    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) onTextChange(listOf(text.trim(), spoken).filter { it.isNotEmpty() }.joinToString(", "))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text("Quick entry") },
            placeholder = { Text("200 chai and samosa, 450 auto") },
            maxLines = 3,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            trailingIcon = if (speechAvailable) {
                {
                    IconButton(onClick = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            // Keep speech on the phone where the recognizer supports it.
                            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say your spends, like \"200 chai, 450 auto\"")
                            .apply { speechLanguage?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) } }
                        try {
                            speechLauncher.launch(intent)
                        } catch (_: ActivityNotFoundException) {
                            // Recognizer vanished between the check and the tap; typing still works.
                        }
                    }) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Speak expenses")
                    }
                }
            } else null,
            shape = ClayFieldShape,
            colors = clayFieldColors(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && !parsing) onSplit() }),
            modifier = Modifier
                .fillMaxWidth()
                .clayField(),
        )
        ClayOutlinedButton(onClick = onSplit, enabled = text.isNotBlank() && !parsing) {
            if (parsing) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Reading…", style = MaterialTheme.typography.labelLarge)
            } else {
                Text("Split into expenses", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** The parsed items, editable, before anything is saved. */
@Composable
fun QuickEntryReview(
    rows: List<QuickRow>,
    fromAi: Boolean,
    onRowChange: (index: Int, QuickRow) -> Unit,
    onRemove: (index: Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (rows.size == 1) "Check this expense" else "Check these ${rows.size} expenses",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (fromAi) AiBadge(description = "Split by on-device AI", label = "On-device AI")
        }
        rows.forEachIndexed { index, row ->
            QuickRowEditor(row, onChange = { onRowChange(index, it) }, onRemove = { onRemove(index) })
        }
    }
}

@Composable
private fun QuickRowEditor(row: QuickRow, onChange: (QuickRow) -> Unit, onRemove: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = row.title,
                onValueChange = { onChange(row.copy(title = it)) },
                singleLine = true,
                isError = row.title.isBlank(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = row.amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("""\d*[.,]?\d{0,2}"""))) onChange(row.copy(amountText = input))
                },
                prefix = { Text(LocalCurrency.current.symbol) },
                singleLine = true,
                isError = row.amount == null,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(120.dp),
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove ${row.title}")
            }
        }
        Box {
            ClayPill(
                text = row.category.label,
                onClick = { menuOpen = true },
                onClickLabel = "Change category",
                leading = { Icon(row.category.icon, contentDescription = null, tint = row.category.color, modifier = Modifier.size(18.dp)) },
                trailing = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = null) },
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Category.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        leadingIcon = { Icon(option.icon, contentDescription = null, tint = option.color) },
                        onClick = {
                            onChange(row.copy(category = option))
                            menuOpen = false
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

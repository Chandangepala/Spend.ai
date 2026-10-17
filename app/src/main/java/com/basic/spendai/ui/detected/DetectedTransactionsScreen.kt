package com.basic.spendai.ui.detected

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.AiState
import com.basic.spendai.data.Category
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.components.AiBadge
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.DuplicateDialog
import com.basic.spendai.ui.components.EmptyState
import com.basic.spendai.ui.components.clay
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import com.basic.spendai.util.formatDate
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedTransactionsScreen(
    detections: List<DetectedTransaction>,
    onAccept: (DetectedTransaction, Category) -> Unit,
    onDismiss: (DetectedTransaction) -> Unit,
    onAcceptAll: () -> Unit,
    onBack: () -> Unit,
    hidden: List<DetectedTransaction> = emptyList(),
    onRestore: (DetectedTransaction) -> Unit = {},
    onRename: (DetectedTransaction, String) -> Unit = { _, _ -> },
    findDuplicate: suspend (amount: Double, timestamp: Long) -> Expense? = { _, _ -> null },
) {
    var showHidden by rememberSaveable { mutableStateOf(false) }
    // Leaving the hidden view goes back to the review list first.
    BackHandler(onBack = { if (showHidden) showHidden = false else onBack() })

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (showHidden) "Hidden by AI" else "Detected from SMS", style = MaterialTheme.typography.titleLarge)
                },
                navigationIcon = {
                    IconButton(onClick = { if (showHidden) showHidden = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!showHidden && hidden.isNotEmpty()) {
                        TextButton(onClick = { showHidden = true }) { Text("Hidden (${hidden.size})") }
                    }
                    if (!showHidden && detections.isNotEmpty()) {
                        TextButton(onClick = onAcceptAll) { Text("Add all") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        DetectedReview(
            detections = detections,
            hidden = hidden,
            showHidden = showHidden,
            onShowHiddenChange = { showHidden = it },
            onAccept = onAccept,
            onDismiss = onDismiss,
            onAcceptAll = onAcceptAll,
            onRestore = onRestore,
            onRename = onRename,
            findDuplicate = findDuplicate,
            showToolbar = false,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * The review list itself, without a screen around it: used full-screen above and inside the
 * Activity tab's "To review" section. [showToolbar] adds the Hidden/Add all row when there's no
 * top app bar to hold them.
 */
@Composable
fun DetectedReview(
    detections: List<DetectedTransaction>,
    hidden: List<DetectedTransaction>,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    onAccept: (DetectedTransaction, Category) -> Unit,
    onDismiss: (DetectedTransaction) -> Unit,
    onAcceptAll: () -> Unit,
    onRestore: (DetectedTransaction) -> Unit,
    onRename: (DetectedTransaction, String) -> Unit,
    findDuplicate: suspend (amount: Double, timestamp: Long) -> Expense?,
    showToolbar: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
) {
    val shown = if (showHidden) hidden else detections
    val scope = rememberCoroutineScope()
    var duplicate by remember { mutableStateOf<Pair<Expense, () -> Unit>?>(null) }
    var renaming by remember { mutableStateOf<DetectedTransaction?>(null) }

    val acceptChecked: (DetectedTransaction, Category) -> Unit = { detection, category ->
        scope.launch {
            val existing = findDuplicate(detection.amount, detection.timestamp)
            if (existing == null) onAccept(detection, category) else duplicate = existing to { onAccept(detection, category) }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 720.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (showToolbar && (showHidden || hidden.isNotEmpty() || detections.isNotEmpty())) {
            item(key = "toolbar") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (showHidden) {
                        TextButton(onClick = { onShowHiddenChange(false) }) { Text("Back to review") }
                    } else {
                        if (hidden.isNotEmpty()) TextButton(onClick = { onShowHiddenChange(true) }) { Text("Hidden (${hidden.size})") }
                        if (detections.isNotEmpty()) TextButton(onClick = onAcceptAll) { Text("Add all") }
                    }
                }
            }
        }
        if (showHidden && shown.isNotEmpty()) {
            item {
                Text(
                    "On-device AI thought these weren't real spends (offers, reminders…). Restore any it got wrong.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (shown.isEmpty()) {
            item(key = "empty") {
                if (showHidden) {
                    EmptyState(icon = Icons.Rounded.VisibilityOff, title = "Nothing hidden", body = "SMS the AI sets aside as offers or reminders appear here.")
                } else {
                    EmptyState(icon = Icons.Rounded.MarkEmailRead, title = "All caught up", body = "New bank SMS will show up here for you to add or dismiss.")
                }
            }
        }
        items(shown, key = { it.id }) { detection ->
            DetectedItem(
                detection = detection,
                hiddenMode = showHidden,
                onAccept = { category -> acceptChecked(detection, category) },
                onDismiss = { onDismiss(detection) },
                onRestore = { onRestore(detection) },
                onRename = { renaming = detection },
                modifier = Modifier.animateItem(),
            )
        }
    }

    duplicate?.let { (existing, add) ->
        DuplicateDialog(
            existing = existing,
            onAddAnyway = {
                add()
                duplicate = null
            },
            onSkip = { duplicate = null },
        )
    }

    renaming?.let { detection ->
        RenameDialog(
            initial = detection.merchant.orEmpty(),
            onSave = { name ->
                onRename(detection, name)
                renaming = null
            },
            onCancel = { renaming = null },
        )
    }
}

@Composable
private fun RenameDialog(initial: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Merchant name") },
        text = {
            OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true)
        },
        confirmButton = { TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectedItem(
    detection: DetectedTransaction,
    hiddenMode: Boolean,
    onAccept: (Category) -> Unit,
    onDismiss: () -> Unit,
    onRestore: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Re-keyed on the suggestion so a learned category from another accept shows up here too.
    var category by remember(detection.id, detection.suggestedCategory) { mutableStateOf(detection.suggestedCategory) }
    var menuOpen by remember { mutableStateOf(false) }
    val surface = MaterialTheme.colorScheme.surface

    ClayCard(color = surface, modifier = modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 8.dp) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            detection.merchant ?: "Bank debit",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable(enabled = !hiddenMode, onClickLabel = "Rename merchant", onClick = onRename),
                        )
                        if (detection.aiState == AiState.AI_DONE) {
                            Spacer(Modifier.size(6.dp))
                            AiBadge()
                        }
                    }
                    Text(
                        "${detection.sender} • ${formatDate(detection.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(8.dp))
                Text(
                    "-${formatCurrency(detection.amount, LocalCurrency.current)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                detection.body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )

            // A FlowRow, not a Row: a long category label ("EMI & Finance") plus both buttons doesn't fit on
            // a phone, and a Row squeezed the last button until "Add" wrapped letter by letter. Here the
            // buttons move to their own line (kept together, on the right) whenever they don't fit.
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                if (!hiddenMode) Box {
                    AssistChip(
                        onClick = { menuOpen = true },
                        label = { Text(category.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(category.icon, contentDescription = null, tint = category.color) },
                        trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, contentDescription = "Change category") },
                    )
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        Category.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                leadingIcon = { Icon(option.icon, contentDescription = null, tint = option.color) },
                                onClick = {
                                    category = option
                                    menuOpen = false
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hiddenMode) {
                        Button(onClick = onRestore) { Text("Restore", maxLines = 1, softWrap = false) }
                    } else {
                        OutlinedButton(onClick = onDismiss) { Text("Dismiss", maxLines = 1, softWrap = false) }
                        Button(onClick = { onAccept(category) }) { Text("Add", maxLines = 1, softWrap = false) }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DetectedTransactionsScreenPreview() {
    SpendaiTheme {
        DetectedTransactionsScreen(
            detections = listOf(
                DetectedTransaction(
                    id = 1,
                    smsKey = "a",
                    sender = "AD-HDFCBK",
                    body = "Rs.250.00 debited from A/c XX1234 to VPA swiggy@icici UPI Ref 123",
                    amount = 250.0,
                    merchant = "Swiggy",
                    suggestedCategory = Category.Food,
                    timestamp = 0,
                ),
            ),
            onAccept = { _, _ -> },
            onDismiss = {},
            onAcceptAll = {},
            onBack = {},
        )
    }
}

package com.basic.spendai.ui.detected

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.clay
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import com.basic.spendai.util.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedTransactionsScreen(
    detections: List<DetectedTransaction>,
    onAccept: (DetectedTransaction, Category) -> Unit,
    onDismiss: (DetectedTransaction) -> Unit,
    onAcceptAll: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Detected from SMS", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (detections.isNotEmpty()) {
                        TextButton(onClick = onAcceptAll) { Text("Add all") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        if (detections.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "All caught up. New bank SMS will show up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .wrapContentWidth(Alignment.CenterHorizontally)
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(detections, key = { it.id }) { detection ->
                    DetectedItem(
                        detection = detection,
                        onAccept = { category -> onAccept(detection, category) },
                        onDismiss = { onDismiss(detection) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectedItem(
    detection: DetectedTransaction,
    onAccept: (Category) -> Unit,
    onDismiss: () -> Unit,
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
                    Text(
                        detection.merchant ?: "Bank debit",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                    color = category.color,
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box {
                    AssistChip(
                        onClick = { menuOpen = true },
                        label = { Text(category.label) },
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
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onDismiss) { Text("Dismiss") }
                Button(onClick = { onAccept(category) }) { Text("Add") }
            }
        }
    }
}

/** Shown on Home when there are SMS detections waiting for review. */
@Composable
fun DetectedBanner(count: Int, onReview: () -> Unit, modifier: Modifier = Modifier) {
    ClayCard(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(12.dp))
            Text(
                if (count == 1) "1 transaction detected from SMS" else "$count transactions detected from SMS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onReview) { Text("Review") }
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

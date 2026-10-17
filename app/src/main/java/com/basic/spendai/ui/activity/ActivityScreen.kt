package com.basic.spendai.ui.activity

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.app.AddExpenseFab
import com.basic.spendai.ui.app.ExpenseActions
import com.basic.spendai.ui.detected.DetectedReview
import com.basic.spendai.ui.home.SwipeToDeleteItem
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class ActivitySection { Expenses, Review }

/** Every expense grouped by day, plus the SMS review list, behind one two-way switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(
    viewModel: ExpenseViewModel,
    actions: ExpenseActions,
    section: ActivitySection,
    onSectionChange: (ActivitySection) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val hidden by viewModel.hiddenDetections.collectAsStateWithLifecycle()
    var showHidden by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Activity", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = { if (section == ActivitySection.Expenses) AddExpenseFab(onClick = actions::add) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            // Full width (up to 480dp): sized to its labels, the row clipped "To review (27)" to "To review".
            SingleChoiceSegmentedButtonRow(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
            ) {
                val pending = state.pendingDetections.size
                listOf(ActivitySection.Expenses to "Expenses", ActivitySection.Review to if (pending > 0) "To review ($pending)" else "To review")
                    .forEachIndexed { i, (value, label) ->
                        SegmentedButton(
                            selected = section == value,
                            onClick = { onSectionChange(value) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                        ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
            }
            when (section) {
                ActivitySection.Expenses -> DayGroupedList(state.expenses, actions, Modifier.weight(1f))
                ActivitySection.Review -> DetectedReview(
                    detections = state.pendingDetections,
                    hidden = hidden,
                    showHidden = showHidden,
                    onShowHiddenChange = { showHidden = it },
                    onAccept = viewModel::acceptDetection,
                    onDismiss = viewModel::dismissDetection,
                    onAcceptAll = viewModel::acceptAllDetections,
                    onRestore = viewModel::restoreDetection,
                    onRename = viewModel::renameDetection,
                    findDuplicate = viewModel::findDuplicate,
                    showToolbar = true,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
                )
            }
        }
    }
}

/** Expenses under pinned day headers ("Today", "Yesterday", "Tue, Oct 14") with each day's total. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayGroupedList(expenses: List<Expense>, actions: ExpenseActions, modifier: Modifier = Modifier) {
    if (expenses.isEmpty()) {
        Box(modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Text(
                "No expenses yet. Tap \"Add expense\" to log one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val zone = remember { ZoneId.systemDefault() }
    val days = remember(expenses) {
        expenses.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }.toSortedMap(compareByDescending { it })
    }
    val currency = LocalCurrency.current
    val listState = rememberLazyListState()
    // At most one row shows its Delete action at a time.
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) openId = null }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .wrapContentWidth(Alignment.CenterHorizontally)
            .widthIn(max = 720.dp),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 112.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        days.forEach { (day, items) ->
            stickyHeader(key = "day-$day") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                ) {
                    Text(dayLabel(day), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        formatCurrency(items.sumOf { it.amount }, currency),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items, key = { it.id }) { expense ->
                key(actions.undoCount) {
                    SwipeToDeleteItem(
                        expense = expense,
                        open = openId == expense.id,
                        onOpenChange = { isOpen -> if (isOpen) openId = expense.id else if (openId == expense.id) openId = null },
                        onDelete = {
                            openId = null
                            actions.delete(expense)
                        },
                        onEdit = { actions.edit(expense) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

internal fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE, MMM d" else "EEE, MMM d, yyyy", Locale.getDefault()))
}

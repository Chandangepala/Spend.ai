package com.basic.spendai.ui.home

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.ExpenseUiState
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.app.AddExpenseFab
import com.basic.spendai.ui.app.ExpenseActions
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.DuplicateDialog
import com.basic.spendai.ui.components.EmptyState
import com.basic.spendai.ui.components.clay
import com.basic.spendai.ui.motion.SpendaiMotion
import com.basic.spendai.ui.motion.staggeredEntrance
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import kotlin.math.abs
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Long enough for the first rows' staggered entrance to finish. */
internal const val ENTRANCE_MS = SpendaiMotion.STAGGER_MAX_ITEMS * SpendaiMotion.STAGGER_MS + 400

/** How many expenses Home lists before "See all" (the full list lives in Activity). */
private const val RECENT_COUNT = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    actions: ExpenseActions,
    onOpenSettings: () -> Unit,
    onSeeAll: () -> Unit,
    onReview: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val insight by viewModel.weeklyInsight.collectAsStateWithLifecycle()
    val layoutInfo = rememberHomeLayoutInfo()
    val currency = LocalCurrency.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Spend.ai",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = { AddExpenseFab(onClick = actions::add) },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            HomeShortcuts(
                toReview = state.pendingDetections.size,
                hasInsight = insight != null,
                onReview = onReview,
                onOpenInsights = onOpenInsights,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
            )
            HomeContent(
                state = ExpenseUiState(expenses = month.expenses, total = month.total),
                layoutInfo = layoutInfo,
                onDelete = actions::delete,
                modifier = Modifier.weight(1f),
                undoCount = actions.undoCount,
                onEdit = actions::edit,
                recent = state.expenses.take(RECENT_COUNT),
                onSeeAll = onSeeAll.takeIf { state.expenses.size > RECENT_COUNT },
                totalLabel = "Spent this month",
                comparison = month.lastMonthToDate?.let { last -> monthComparison(month.total, last, currency) },
            )
        }
    }
}

/** "₹1,200 less than this time last month", or null when there's nothing to compare. */
internal fun monthComparison(thisMonth: Double, lastMonthToDate: Double, currency: AppCurrency): String? {
    val diff = thisMonth - lastMonthToDate
    if (lastMonthToDate == 0.0 && thisMonth == 0.0) return null
    if (abs(diff) < 0.005) return "Same as this time last month"
    val amount = formatCurrency(abs(diff), currency)
    return "$amount ${if (diff < 0) "less" else "more"} than this time last month"
}

/**
 * Small one-tap shortcuts instead of full-width cards: what needs attention (SMS to review) and
 * the weekly summary. Nothing shows when there's nothing to say.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeShortcuts(
    toReview: Int,
    hasInsight: Boolean,
    onReview: () -> Unit,
    onOpenInsights: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (toReview == 0 && !hasInsight) return
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (toReview > 0) {
            AssistChip(
                onClick = onReview,
                label = { Text(if (toReview == 1) "1 SMS to review" else "$toReview SMS to review") },
                leadingIcon = { Icon(Icons.Rounded.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            )
        }
        if (hasInsight) {
            AssistChip(
                onClick = onOpenInsights,
                label = { Text("This week's summary") },
                leadingIcon = { Icon(Icons.Rounded.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            )
        }
    }
}

@Composable
fun HomeContent(
    state: ExpenseUiState,
    layoutInfo: HomeLayoutInfo,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier,
    undoCount: Int = 0,
    onEdit: (Expense) -> Unit = {},
    /** Rows listed beside the summary; defaults to everything in [state]. */
    recent: List<Expense> = state.expenses,
    onSeeAll: (() -> Unit)? = null,
    totalLabel: String = "Total Spent",
    comparison: String? = null,
) {
    when (layoutInfo.layout) {
        HomeLayout.SinglePane -> Column(modifier.fillMaxSize()) {
            TotalSpentCard(
                total = state.total,
                expenses = state.expenses,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                label = totalLabel,
                comparison = comparison,
            )
            TransactionsPane(recent, onDelete, Modifier.weight(1f), undoCount, onEdit, onSeeAll)
        }

        HomeLayout.TwoPane -> HingeAwareRow(
            hinge = layoutInfo.hinge,
            modifier = modifier,
            start = { SummaryPane(state, Modifier.fillMaxSize(), label = totalLabel, comparison = comparison) },
            end = {
                TransactionsPane(
                    expenses = recent,
                    onDelete = onDelete,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 720.dp),
                    undoCount = undoCount,
                    onEdit = onEdit,
                    onSeeAll = onSeeAll,
                )
            },
        )

        HomeLayout.Tabletop -> HingeAwareColumn(
            hinge = layoutInfo.hinge,
            modifier = modifier,
            top = { SummaryPane(state, Modifier.fillMaxSize(), sideBySide = true, label = totalLabel, comparison = comparison) },
            bottom = { TransactionsPane(recent, onDelete, Modifier.fillMaxSize(), undoCount, onEdit, onSeeAll) },
        )
    }
}

/** Total card plus the per-category breakdown, used when there is room beside or above the list. */
@Composable
private fun SummaryPane(
    state: ExpenseUiState,
    modifier: Modifier = Modifier,
    sideBySide: Boolean = false,
    label: String = "Total Spent",
    comparison: String? = null,
) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TotalSpentCard(state.total, state.expenses, Modifier.weight(1f), showLegend = false, label = label, comparison = comparison)
                CategoryBreakdown(state.expenses, Modifier.weight(1f))
            }
        } else {
            TotalSpentCard(state.total, state.expenses, showLegend = false, label = label, comparison = comparison)
            CategoryBreakdown(state.expenses)
        }
    }
}

@Composable
private fun TransactionsPane(
    expenses: List<Expense>,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier,
    undoCount: Int = 0,
    onEdit: (Expense) -> Unit = {},
    onSeeAll: (() -> Unit)? = null,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Recent transactions",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp),
            )
            onSeeAll?.let { TextButton(onClick = it) { Text("See all") } }
        }

        if (expenses.isEmpty()) {
            EmptyState(Modifier.weight(1f))
        } else {
            val listState = rememberLazyListState()
            // The list keeps its position by key, so new expenses land above the visible rows.
            // Scroll up when a newer one arrives (ids only grow; deletes don't trigger this).
            val newestId = expenses.first().id
            var lastNewestId by rememberSaveable { mutableLongStateOf(newestId) }
            LaunchedEffect(newestId) {
                if (newestId > lastNewestId) listState.animateScrollToItem(0)
                lastNewestId = newestId
            }
            // At most one row shows its Delete action at a time.
            var openId by rememberSaveable { mutableStateOf<Long?>(null) }
            LaunchedEffect(listState.isScrollInProgress) { if (listState.isScrollInProgress) openId = null }
            // Rows stagger in only the first time this list appears (not on tab switches or scrolling).
            var entered by rememberSaveable { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                delay(ENTRANCE_MS)
                entered = true
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(expenses, key = { _, e -> e.id }) { index, expense ->
                    // An undone row comes back with the same id, so the list would reuse its
                    // swiped-away state; a new undoCount gives every row a fresh, settled state.
                    key(undoCount) {
                        SwipeToDeleteItem(
                            expense = expense,
                            open = openId == expense.id,
                            onOpenChange = { isOpen ->
                                if (isOpen) openId = expense.id else if (openId == expense.id) openId = null
                            },
                            onDelete = {
                                openId = null
                                onDelete(expense)
                            },
                            onEdit = { onEdit(expense) },
                            modifier = Modifier
                                .animateItem()
                                .staggeredEntrance(index, enabled = !entered),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SwipeToDeleteItem(
    expense: Expense,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SwipeToRevealDelete(open = open, onOpenChange = onOpenChange, onDelete = onDelete, modifier = modifier) { closeInstead ->
        // While the Delete action is showing, a tap on the row just closes it.
        TransactionItem(expense, onClick = closeInstead ?: onEdit)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Rounded.ReceiptLong,
        title = "No expenses yet",
        body = "Tap \"Add expense\" to log your first one, or turn on SMS detection in Settings.",
        modifier = modifier,
    )
}

private val previewState = ExpenseUiState(
    expenses = listOf(
        Expense(id = 1, title = "Lunch", amount = 450.0, category = Category.Food),
        Expense(id = 2, title = "Metro card", amount = 280.0, category = Category.Transport),
        Expense(id = 3, title = "Sneakers", amount = 1000.0, category = Category.Shopping),
    ),
    total = 1730.0,
)

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Composable
private fun HomeContentPhonePreview() {
    SpendaiTheme { HomeContent(previewState, HomeLayoutInfo(HomeLayout.SinglePane), onDelete = {}) }
}

@Preview(name = "Foldable unfolded", device = Devices.FOLDABLE, showBackground = true)
@Composable
private fun HomeContentFoldablePreview() {
    SpendaiTheme { HomeContent(previewState, HomeLayoutInfo(HomeLayout.TwoPane), onDelete = {}) }
}

@Preview(name = "Foldable tabletop", device = Devices.FOLDABLE, showBackground = true)
@Composable
private fun HomeContentTabletopPreview() {
    SpendaiTheme { HomeContent(previewState, HomeLayoutInfo(HomeLayout.Tabletop), onDelete = {}) }
}

@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
private fun HomeContentTabletPreview() {
    SpendaiTheme { HomeContent(previewState, HomeLayoutInfo(HomeLayout.TwoPane), onDelete = {}) }
}

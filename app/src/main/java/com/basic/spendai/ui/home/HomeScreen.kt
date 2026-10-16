package com.basic.spendai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.ExpenseUiState
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.DuplicateDialog
import com.basic.spendai.ui.detected.DetectedBanner
import com.basic.spendai.ui.components.clay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: ExpenseViewModel,
    onOpenSettings: () -> Unit,
    onOpenDetected: () -> Unit = {},
    onOpenAsk: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val categoryOrder by viewModel.categoryOrder.collectAsStateWithLifecycle()
    val insight by viewModel.weeklyInsight.collectAsStateWithLifecycle()
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    // Bumped on every Undo so list rows forget the restored row's "swiped away" state.
    var undoCount by remember { mutableIntStateOf(0) }
    val deleteWithUndo: (Expense) -> Unit = { expense ->
        viewModel.deleteExpense(expense)
        // A newer delete replaces the previous snackbar (and its undo).
        undoJob?.cancel()
        undoJob = scope.launch {
            // Snackbar durations are fixed (4s+), so cap it at 3s ourselves; cancelling dismisses it.
            val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                snackbarHostState.showSnackbar(
                    message = "Deleted \"${expense.title}\"",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreExpense(expense)
                undoCount++
            }
        }
    }
    // Possible duplicates waiting for "Add anyway / Skip"; quick entry can queue several.
    val duplicates = remember { mutableStateListOf<Pair<Expense, () -> Unit>>() }
    val layoutInfo = rememberHomeLayoutInfo()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    IconButton(onClick = onOpenAsk) {
                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = "Ask your spends")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Add expense") },
                shape = RoundedCornerShape(24.dp),
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                modifier = Modifier.clay(MaterialTheme.colorScheme.primary, cornerRadius = 24.dp, elevation = 12.dp),
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            if (state.pendingDetections.isNotEmpty()) {
                DetectedBanner(
                    count = state.pendingDetections.size,
                    onReview = onOpenDetected,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }
            insight?.let {
                InsightCard(it, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            }
            HomeContent(
                state = state,
                layoutInfo = layoutInfo,
                onDelete = deleteWithUndo,
                modifier = Modifier.weight(1f),
                undoCount = undoCount,
                onEdit = { editing = it },
            )
        }
    }

    if (showAddSheet) {
        AddExpenseSheet(
            onDismiss = { showAddSheet = false },
            onSave = { title, amount, category ->
                scope.launch { addChecked(viewModel, title, amount, category, duplicates) }
            },
            onQuickParse = viewModel::parseQuickEntry,
            onScanReceipt = viewModel::scanReceipt,
            categoryOrder = categoryOrder,
            onSaveMany = { items ->
                scope.launch { items.forEach { addChecked(viewModel, it.title, it.amount, it.category, duplicates) } }
            },
        )
    }

    editing?.let { expense ->
        // Keyed so tapping another row while one is open starts from that row's values.
        key(expense.id) {
            AddExpenseSheet(
                onDismiss = { editing = null },
                onSave = { _, _, _ -> },
                editing = expense,
                onUpdate = viewModel::updateExpense,
                categoryOrder = categoryOrder,
            )
        }
    }

    duplicates.firstOrNull()?.let { (existing, add) ->
        DuplicateDialog(
            existing = existing,
            onAddAnyway = {
                add()
                duplicates.removeAt(0)
            },
            onSkip = { duplicates.removeAt(0) },
        )
    }
}

/** How long the "Undo" option stays after a swipe-delete. */
private const val UNDO_WINDOW_MS = 3_000L

/** Adds right away, or queues the "Looks like a duplicate" question if a similar expense exists. */
private suspend fun addChecked(
    viewModel: ExpenseViewModel,
    title: String,
    amount: Double,
    category: Category,
    duplicates: MutableList<Pair<Expense, () -> Unit>>,
) {
    val add = { viewModel.addExpense(title, amount, category) }
    val existing = viewModel.findDuplicate(amount, System.currentTimeMillis())
    if (existing == null) add() else duplicates += existing to add
}

@Composable
fun HomeContent(
    state: ExpenseUiState,
    layoutInfo: HomeLayoutInfo,
    onDelete: (Expense) -> Unit,
    modifier: Modifier = Modifier,
    undoCount: Int = 0,
    onEdit: (Expense) -> Unit = {},
) {
    when (layoutInfo.layout) {
        HomeLayout.SinglePane -> Column(modifier.fillMaxSize()) {
            TotalSpentCard(
                total = state.total,
                expenses = state.expenses,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
            TransactionsPane(state.expenses, onDelete, Modifier.weight(1f), undoCount, onEdit)
        }

        HomeLayout.TwoPane -> HingeAwareRow(
            hinge = layoutInfo.hinge,
            modifier = modifier,
            start = { SummaryPane(state, Modifier.fillMaxSize()) },
            end = {
                TransactionsPane(
                    expenses = state.expenses,
                    onDelete = onDelete,
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentWidth(Alignment.CenterHorizontally)
                        .widthIn(max = 720.dp),
                    undoCount = undoCount,
                    onEdit = onEdit,
                )
            },
        )

        HomeLayout.Tabletop -> HingeAwareColumn(
            hinge = layoutInfo.hinge,
            modifier = modifier,
            top = { SummaryPane(state, Modifier.fillMaxSize(), sideBySide = true) },
            bottom = { TransactionsPane(state.expenses, onDelete, Modifier.fillMaxSize(), undoCount, onEdit) },
        )
    }
}

/** Total card plus the per-category breakdown, used when there is room beside or above the list. */
@Composable
private fun SummaryPane(state: ExpenseUiState, modifier: Modifier = Modifier, sideBySide: Boolean = false) {
    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        if (sideBySide) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                TotalSpentCard(state.total, state.expenses, Modifier.weight(1f), showLegend = false)
                CategoryBreakdown(state.expenses, Modifier.weight(1f))
            }
        } else {
            TotalSpentCard(state.total, state.expenses, showLegend = false)
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
) {
    Column(modifier) {
        Text(
            "Recent transactions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
        )

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
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(expenses, key = { it.id }) { expense ->
                    // An undone row comes back with the same id, so the list would reuse its
                    // swiped-away state; a new undoCount gives every row a fresh, settled state.
                    key(undoCount) {
                        SwipeToDeleteItem(
                            expense = expense,
                            onDelete = { onDelete(expense) },
                            onEdit = { onEdit(expense) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteItem(expense: Expense, onDelete: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) {
                onDelete()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onErrorContainer)
                Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        TransactionItem(expense, onClick = onEdit)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ClayCard(
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp),
                cornerRadius = 48.dp,
            ) {
                Icon(
                    Icons.Rounded.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(44.dp)
                        .align(Alignment.Center),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("No expenses yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap \"Add expense\" to log your first one.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
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

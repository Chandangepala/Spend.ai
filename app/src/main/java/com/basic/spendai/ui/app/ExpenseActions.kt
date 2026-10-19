package com.basic.spendai.ui.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.components.DuplicateDialog
import com.basic.spendai.ui.components.clay
import com.basic.spendai.ui.components.ClayTokens
import com.basic.spendai.ui.theme.LocalClayPalette
import androidx.compose.foundation.shape.CircleShape
import com.basic.spendai.ui.home.AddExpenseSheet
import com.basic.spendai.ui.motion.LocalChromeVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long the "Undo" option stays after a delete. */
private const val UNDO_WINDOW_MS = 3_000L

/**
 * Add / edit / delete-with-undo, shared by every tab that lists expenses. The sheets and dialogs
 * live once at app level ([ExpenseActionsHost]) so Home and Activity behave identically.
 */
@Stable
class ExpenseActions(
    private val scope: CoroutineScope,
    private val viewModel: ExpenseViewModel,
    val snackbar: SnackbarHostState,
) {
    var addOpen by mutableStateOf(false)
        private set
    var editing by mutableStateOf<Expense?>(null)
        private set

    /** Bumped on every Undo so list rows forget the restored row's swipe state. */
    var undoCount by mutableIntStateOf(0)
        private set

    /** Possible duplicates waiting for "Add anyway / Skip"; quick entry can queue several. */
    internal val duplicates = mutableStateListOf<Pair<Expense, () -> Unit>>()
    private var undoJob: Job? = null

    fun add() { addOpen = true }
    fun closeAdd() { addOpen = false }
    fun edit(expense: Expense) { editing = expense }
    fun closeEdit() { editing = null }

    fun delete(expense: Expense) {
        viewModel.deleteExpense(expense)
        // A newer delete replaces the previous snackbar (and its undo).
        undoJob?.cancel()
        undoJob = scope.launch {
            // Snackbar durations are fixed (4s+), so cap it at 3s ourselves; cancelling dismisses it.
            val result = withTimeoutOrNull(UNDO_WINDOW_MS) {
                snackbar.showSnackbar(
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

    /** Adds right away, or queues the "Looks like a duplicate" question if a similar expense exists. */
    internal fun addChecked(title: String, amount: Double, category: Category) {
        scope.launch {
            val add = { viewModel.addExpense(title, amount, category) }
            val existing = viewModel.findDuplicate(amount, System.currentTimeMillis())
            if (existing == null) add() else duplicates += existing to add
        }
    }
}

@Composable
fun rememberExpenseActions(viewModel: ExpenseViewModel): ExpenseActions {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    return remember(viewModel) { ExpenseActions(scope, viewModel, snackbar) }
}

/** The add sheet, edit sheet and duplicate prompt; render once at app level. */
@Composable
fun ExpenseActionsHost(actions: ExpenseActions, viewModel: ExpenseViewModel) {
    val categoryOrder by viewModel.categoryOrder.collectAsStateWithLifecycle()

    if (actions.addOpen) {
        AddExpenseSheet(
            onDismiss = actions::closeAdd,
            onSave = { title, amount, category -> actions.addChecked(title, amount, category) },
            onQuickParse = viewModel::parseQuickEntry,
            onScanReceipt = viewModel::scanReceipt,
            categoryOrder = categoryOrder,
            onSaveMany = { items -> items.forEach { actions.addChecked(it.title, it.amount, it.category) } },
        )
    }

    actions.editing?.let { expense ->
        // Keyed so tapping another row while one is open starts from that row's values.
        key(expense.id) {
            AddExpenseSheet(
                onDismiss = actions::closeEdit,
                onSave = { _, _, _ -> },
                editing = expense,
                onUpdate = viewModel::updateExpense,
                categoryOrder = categoryOrder,
            )
        }
    }

    actions.duplicates.firstOrNull()?.let { (existing, add) ->
        DuplicateDialog(
            existing = existing,
            onAddAnyway = {
                add()
                actions.duplicates.removeAt(0)
            },
            onSkip = { actions.duplicates.removeAt(0) },
        )
    }
}

/** The clay "Add expense" button used on Home and Activity; shrinks to "+" while the bar is hidden. */
@Composable
fun AddExpenseFab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalClayPalette.current
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = LocalChromeVisible.current,
        icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
        text = { Text("Add expense") },
        shape = CircleShape,
        containerColor = Color.Transparent,
        contentColor = palette.onEmerald,
        elevation = FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
        // The emerald clay pill: gradient fill, green-tinted drop shadow.
        modifier = modifier.clay(
            color = MaterialTheme.colorScheme.primary,
            cornerRadius = ClayTokens.PillRadius,
            elevation = ClayTokens.RaisedElevation,
            shadowColor = palette.heroShadow.copy(alpha = 0.6f),
            brush = palette.button,
        ),
    )
}

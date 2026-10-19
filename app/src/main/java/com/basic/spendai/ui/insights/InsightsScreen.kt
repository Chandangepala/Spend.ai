package com.basic.spendai.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basic.spendai.budgets.BudgetProgress
import com.basic.spendai.data.Category
import com.basic.spendai.data.SavingsGoal
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.budgets.AddMoneyDialog
import com.basic.spendai.ui.budgets.BudgetDialog
import com.basic.spendai.ui.budgets.BudgetsCard
import com.basic.spendai.ui.budgets.GoalDialog
import com.basic.spendai.ui.budgets.GoalsCard
import com.basic.spendai.ui.budgets.WhatIfCard
import com.basic.spendai.ui.components.EmptyState
import com.basic.spendai.ui.components.TabHeader
import com.basic.spendai.ui.home.CategoryBreakdown
import com.basic.spendai.ui.home.InsightCard

/**
 * The weekly summary, the monthly trend, where this month's money went, budgets and goals, and
 * repeating payments. [focusBudgets] scrolls to the budgets each time it changes (Home's chips).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: ExpenseViewModel, onOpenSettings: () -> Unit, focusBudgets: Int = 0) {
    val insight by viewModel.weeklyInsight.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val months by viewModel.monthlyTotals.collectAsStateWithLifecycle()
    val repeating by viewModel.repeatingPayments.collectAsStateWithLifecycle()
    val budgets by viewModel.budgets.collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val categoryOrder by viewModel.categoryOrder.collectAsStateWithLifecycle()
    val all by viewModel.uiState.collectAsStateWithLifecycle()

    var budgetDialog by remember { mutableStateOf<BudgetEdit?>(null) }
    var goalDialog by remember { mutableStateOf<GoalEdit?>(null) }
    var addingTo by remember { mutableStateOf<SavingsGoal?>(null) }
    val listState = rememberLazyListState()
    val empty = months.all { it.total == 0.0 } && repeating == null
    // Remember the last handled request, so returning to the tab doesn't scroll again.
    var handledFocus by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(focusBudgets) {
        if (focusBudgets != handledFocus) {
            handledFocus = focusBudgets
            listState.animateScrollToItem(if (empty) 1 else BUDGETS_INDEX)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { TabHeader("Insights", onOpenSettings) },
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val budgetsAndGoals: () -> Unit = {
                item(key = "budgets") {
                    Text("Budgets & goals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                }
                item(key = "budgetList") {
                    val free = categoryOrder.filter { c -> budgets.none { it.category == c } }
                    BudgetsCard(
                        budgets = budgets,
                        onAdd = { budgetDialog = BudgetEdit(null) },
                        onEdit = { budgetDialog = BudgetEdit(it) },
                        canAdd = free.isNotEmpty(),
                    )
                }
                if (!empty) {
                    item(key = "whatIf") {
                        WhatIfCard(categoryOrder, viewModel::whatIf, viewModel::coach, dataVersion = all.expenses.size)
                    }
                }
                item(key = "goals") {
                    GoalsCard(
                        goals = goals,
                        onAdd = { goalDialog = GoalEdit(null) },
                        onEdit = { goalDialog = GoalEdit(it) },
                        onAddMoney = { addingTo = it },
                        onCoach = viewModel::coach,
                    )
                }
            }
            if (empty) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Insights,
                        title = "Insights appear as you spend",
                        body = "Add a few expenses and this tab fills in: your week, your months, and where the money goes.",
                    )
                }
                budgetsAndGoals()
                return@LazyColumn
            }
            item(key = "week") {
                insight?.let { InsightCard(it) } ?: Text(
                    "Your weekly summary appears here after your first week of spending.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "trend") { MonthlyTrendCard(months) }
            item(key = "month") {
                Text("This month", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
            }
            item(key = "breakdown") { CategoryBreakdown(month.expenses) }
            budgetsAndGoals()
            item(key = "repeating") { RepeatingPaymentsCard(repeating) }
        }
    }

    budgetDialog?.let { edit ->
        BudgetDialog(
            editing = edit.budget,
            available = categoryOrder.filter { c -> budgets.none { it.category == c } }.ifEmpty { Category.entries },
            onSuggest = viewModel::suggestBudget,
            onCoach = viewModel::coach,
            onSave = viewModel::setBudget,
            onRemove = viewModel::removeBudget,
            onDismiss = { budgetDialog = null },
        )
    }
    goalDialog?.let { edit ->
        GoalDialog(edit.goal, onSave = viewModel::saveGoal, onDelete = viewModel::deleteGoal, onDismiss = { goalDialog = null })
    }
    addingTo?.let { goal ->
        AddMoneyDialog(goal, onAdd = { viewModel.addToGoal(goal, it) }, onDismiss = { addingTo = null })
    }
}

/** Week, trend, "This month" and breakdown come before the budgets header. */
private const val BUDGETS_INDEX = 4

/** Wrappers so "add" (null) differs from "closed". */
private data class BudgetEdit(val budget: BudgetProgress?)
private data class GoalEdit(val goal: SavingsGoal?)

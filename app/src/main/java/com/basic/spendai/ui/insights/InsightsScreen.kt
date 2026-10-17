package com.basic.spendai.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.home.CategoryBreakdown
import com.basic.spendai.ui.home.InsightCard

/** The weekly summary, the monthly trend, where this month's money went, and repeating payments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(viewModel: ExpenseViewModel, onOpenSettings: () -> Unit) {
    val insight by viewModel.weeklyInsight.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val months by viewModel.monthlyTotals.collectAsStateWithLifecycle()
    val repeating by viewModel.repeatingPayments.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Insights", style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
            // Budgets (Phase 7) will slot in here, next to where the money goes.
            item(key = "repeating") { RepeatingPaymentsCard(repeating) }
        }
    }
}

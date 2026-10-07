package com.basic.spendai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.data.ExpenseDatabase
import com.basic.spendai.data.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val total: Double = 0.0,
    val currency: AppCurrency = AppCurrency.default(),
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = ExpenseDatabase.get(application).expenseDao()
    private val settings = SettingsRepository(application)

    val uiState: StateFlow<ExpenseUiState> =
        combine(dao.observeAll(), dao.observeTotal(), settings.currency) { expenses, total, currency ->
            ExpenseUiState(expenses, total ?: 0.0, currency)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    fun addExpense(title: String, amount: Double, category: Category) {
        viewModelScope.launch {
            dao.insert(Expense(title = title.trim(), amount = amount, category = category))
        }
    }

    fun setCurrency(currency: AppCurrency) {
        viewModelScope.launch { settings.setCurrency(currency) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { dao.delete(expense) }
    }
}

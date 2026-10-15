package com.basic.spendai.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.basic.spendai.ai.AiProviders
import com.basic.spendai.ai.AiStatus
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.data.DetectionStatus
import com.basic.spendai.data.Expense
import com.basic.spendai.data.ExpenseDatabase
import com.basic.spendai.data.MerchantCategory
import com.basic.spendai.data.SettingsRepository
import com.basic.spendai.sms.CategoryGuesser
import com.basic.spendai.sms.SmsImportRepository
import com.basic.spendai.sms.SmsInboxScanner
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiUiState(
    val enabled: Boolean = true,
    val status: AiStatus = AiStatus.Unavailable,
)

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val total: Double = 0.0,
    val currency: AppCurrency = AppCurrency.default(),
    val pendingDetections: List<DetectedTransaction> = emptyList(),
    val smsImportEnabled: Boolean = false,
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ExpenseDatabase.get(application)
    private val dao = db.expenseDao()
    private val detectedDao = db.detectedTransactionDao()
    private val settings = SettingsRepository(application)

    val uiState: StateFlow<ExpenseUiState> =
        combine(
            dao.observeAll(),
            dao.observeTotal(),
            settings.currency,
            detectedDao.observePending(),
            settings.smsImportEnabled,
        ) { expenses, total, currency, pending, smsEnabled ->
            ExpenseUiState(expenses, total ?: 0.0, currency, pending, smsEnabled)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    val hiddenDetections: StateFlow<List<DetectedTransaction>> =
        detectedDao.observeHidden().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val aiStatus = MutableStateFlow<AiStatus>(AiStatus.Unavailable)
    private var aiDownload: Job? = null

    val aiState: StateFlow<AiUiState> =
        combine(settings.aiEnabled, aiStatus) { enabled, status -> AiUiState(enabled, status) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiUiState())

    /** Re-checks whether Gemini Nano is ready; cheap, so it runs whenever Settings opens. */
    fun refreshAiStatus() {
        if (aiDownload?.isActive == true) return
        viewModelScope.launch { aiStatus.value = AiProviders.nano.status() }
    }

    fun downloadAiModel() {
        if (aiDownload?.isActive == true) return
        aiDownload = viewModelScope.launch {
            AiProviders.nano.download().collect { aiStatus.value = it }
        }
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch { settings.setAiEnabled(enabled) }
    }

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

    fun setSmsImportEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setSmsImportEnabled(enabled)
            if (enabled) scanAndEnrich()
        }
    }

    /**
     * Picks up SMS that arrived while the receiver couldn't run (e.g. before permission was granted),
     * then lets the on-device model tidy pending ones. Called on every resume, since Gemini Nano
     * only runs while the app is in the foreground.
     */
    fun scanSms() {
        viewModelScope.launch {
            if (settings.smsImportEnabled.first()) scanAndEnrich()
        }
    }

    private suspend fun scanAndEnrich() {
        val app = getApplication<Application>()
        SmsInboxScanner.scan(app)
        SmsImportRepository(app).enrichPending(AiProviders.best(app))
    }

    /** An existing expense that looks like the same spend: amount within ±1, time within a day. */
    suspend fun findDuplicate(amount: Double, timestamp: Long): Expense? =
        dao.findNear(amount - 1, amount + 1, timestamp - DAY_MS, timestamp + DAY_MS).firstOrNull()

    fun restoreDetection(detection: DetectedTransaction) {
        viewModelScope.launch { detectedDao.updateStatus(detection.id, DetectionStatus.PENDING) }
    }

    fun renameDetection(detection: DetectedTransaction, merchant: String) {
        val name = merchant.trim()
        if (name.isEmpty()) return
        viewModelScope.launch { detectedDao.updateMerchant(detection.id, name) }
    }

    fun acceptDetection(detection: DetectedTransaction, category: Category) {
        viewModelScope.launch { accept(detection, category) }
    }

    /** Adds every pending detection except possible duplicates, which stay for one-by-one review. */
    fun acceptAllDetections() {
        viewModelScope.launch {
            uiState.value.pendingDetections
                .filter { findDuplicate(it.amount, it.timestamp) == null }
                .forEach { accept(it, it.suggestedCategory) }
        }
    }

    fun dismissDetection(detection: DetectedTransaction) {
        viewModelScope.launch { detectedDao.updateStatus(detection.id, DetectionStatus.DISMISSED) }
    }

    private suspend fun accept(detection: DetectedTransaction, category: Category) {
        db.withTransaction {
            dao.insert(
                Expense(
                    title = detection.merchant ?: "Bank debit",
                    amount = detection.amount,
                    category = category,
                    timestamp = detection.timestamp,
                )
            )
            detectedDao.updateStatus(detection.id, DetectionStatus.ACCEPTED)
            // Remember a correction so this merchant's next SMS (and any still pending) get it.
            val merchant = detection.merchant
            if (merchant != null && category != detection.suggestedCategory) {
                detectedDao.upsertOverride(MerchantCategory(CategoryGuesser.merchantKey(merchant), category))
                detectedDao.updatePendingCategory(merchant, category)
            }
        }
    }

    private companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
    }
}

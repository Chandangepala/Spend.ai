package com.basic.spendai.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.basic.spendai.ai.AiProviders
import com.basic.spendai.ai.AiResult
import com.basic.spendai.ai.QuickEntryItem
import com.basic.spendai.ai.QuickEntryParser
import com.basic.spendai.ai.ReceiptItemizer
import com.basic.spendai.ai.ReceiptResult
import com.basic.spendai.ai.SpendQueryTranslator
import com.basic.spendai.ai.AiStatus
import com.basic.spendai.ai.AnswerWriter
import com.basic.spendai.data.AccuracyStats
import com.basic.spendai.data.AiState
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.CategoryCorrection
import com.basic.spendai.data.CorrectionSource
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.data.DetectionStatus
import com.basic.spendai.data.Expense
import com.basic.spendai.data.ExpenseDatabase
import com.basic.spendai.data.MerchantAlias
import com.basic.spendai.data.MerchantCategory
import com.basic.spendai.data.SettingsRepository
import com.basic.spendai.receipt.ReceiptPhotos
import com.basic.spendai.receipt.ReceiptScanner
import com.basic.spendai.sms.CategoryGuesser
import com.basic.spendai.sms.SmsImportRepository
import com.basic.spendai.sms.SmsInboxScanner
import com.basic.spendai.sms.SmsParser
import com.basic.spendai.stats.SpendAnswer
import com.basic.spendai.stats.SpendQueryExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiUiState(
    val enabled: Boolean = true,
    val status: AiStatus = AiStatus.Unavailable,
    /** Share of AI / rules suggestions the user kept unchanged over the last 30 days. */
    val aiAccuracy: AccuracyStats = AccuracyStats(0, 0),
    val rulesAccuracy: AccuracyStats = AccuracyStats(0, 0),
)

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val total: Double = 0.0,
    val currency: AppCurrency = AppCurrency.default(),
    val pendingDetections: List<DetectedTransaction> = emptyList(),
    val smsImportEnabled: Boolean = false,
)

/** One question and its answer on the Ask screen. */
data class AskEntry(
    val id: Long,
    val question: String,
    val sentence: String,
    val answer: SpendAnswer,
    /** The model translated the question or wrote the sentence (numbers always come from code). */
    val fromAi: Boolean,
)

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val db = ExpenseDatabase.get(application)
    private val dao = db.expenseDao()
    private val detectedDao = db.detectedTransactionDao()
    private val correctionDao = db.correctionDao()
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

    private val accuracySince = System.currentTimeMillis() - ACCURACY_WINDOW_MS

    val aiState: StateFlow<AiUiState> =
        combine(
            settings.aiEnabled,
            aiStatus,
            correctionDao.observeAccuracy(CorrectionSource.AI, accuracySince),
            correctionDao.observeAccuracy(CorrectionSource.RULES, accuracySince),
        ) { enabled, status, ai, rules -> AiUiState(enabled, status, ai, rules) }
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

    /** Splits a note like "200 chai, 450 auto" into expenses to confirm; rules when AI isn't available. */
    suspend fun parseQuickEntry(text: String): AiResult<List<QuickEntryItem>> =
        withContext(Dispatchers.Default) { QuickEntryParser.run(AiProviders.best(getApplication()), text) }

    /**
     * Reads a receipt photo on the device and finds its total (and items, when the model can split
     * them and they add up). Null when no text could be read. Deletes our own camera photo afterwards.
     */
    suspend fun scanReceipt(uri: Uri): AiResult<ReceiptResult>? {
        val app = getApplication<Application>()
        val text = ReceiptScanner.readText(app, uri)
        ReceiptPhotos.delete(app, uri)
        if (text == null) return null
        return withContext(Dispatchers.Default) { ReceiptItemizer.run(AiProviders.best(app), text) }
    }

    private val _askHistory = MutableStateFlow<List<AskEntry>>(emptyList())
    val askHistory: StateFlow<List<AskEntry>> = _askHistory
    private val _asking = MutableStateFlow(false)
    val asking: StateFlow<Boolean> = _asking

    /** Question -> query (model or rules) -> numbers (code) -> sentence (model or template). */
    fun ask(question: String) {
        if (_asking.value) return
        _asking.value = true
        viewModelScope.launch {
            try {
                val provider = AiProviders.best(getApplication())
                val query = withContext(Dispatchers.Default) { SpendQueryTranslator().run(provider, question) }
                val answer = SpendQueryExecutor(dao::between).run(query.value)
                val input = AnswerWriter.Input(question, answer, settings.currency.first())
                val sentence = withContext(Dispatchers.Default) { AnswerWriter.run(provider, input) }
                val entry = AskEntry(System.nanoTime(), question, sentence.value, answer, query.fromAi || sentence.fromAi)
                _askHistory.value = listOf(entry) + _askHistory.value
            } finally {
                _asking.value = false
            }
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

    /** Renames this row and remembers the name for the raw SMS merchant, so future SMS get it too. */
    fun renameDetection(detection: DetectedTransaction, merchant: String) {
        val name = merchant.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            // Rows saved before raw merchants were stored: recover the raw text from the SMS itself.
            val raw = detection.rawMerchant ?: SmsParser.parse(detection.body)?.rawMerchant
            db.withTransaction {
                detectedDao.updateMerchant(detection.id, name)
                raw?.let { correctionDao.upsertAlias(MerchantAlias(CategoryGuesser.merchantKey(it), name)) }
                detectedDao.renamePending(raw, detection.merchant, name)
            }
        }
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
            // Every review is a data point: unchanged ones measure accuracy, changed ones teach the model.
            correctionDao.insert(
                CategoryCorrection(
                    merchant = detection.merchant,
                    smsSnippet = detection.body.take(SNIPPET_CHARS),
                    suggested = detection.suggestedCategory,
                    chosen = category,
                    source = if (detection.aiState == AiState.AI_DONE) CorrectionSource.AI else CorrectionSource.RULES,
                )
            )
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
        const val ACCURACY_WINDOW_MS = 30 * DAY_MS
        const val SNIPPET_CHARS = 160
    }
}

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
import com.basic.spendai.ai.InsightWriter
import com.basic.spendai.ai.GemmaModel
import com.basic.spendai.ai.GemmaState
import com.basic.spendai.ai.CoachWriter
import com.basic.spendai.reminders.DailyReminder
import com.basic.spendai.reminders.ReminderReceiver
import com.basic.spendai.budgets.BudgetLevel
import com.basic.spendai.budgets.BudgetMath
import com.basic.spendai.budgets.BudgetNudges
import com.basic.spendai.budgets.BudgetProgress
import com.basic.spendai.data.Budget
import com.basic.spendai.data.BudgetNudge
import com.basic.spendai.data.SavingsGoal
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
import com.basic.spendai.insights.Finding
import com.basic.spendai.insights.InsightsEngine
import com.basic.spendai.insights.InsightsRepository
import com.basic.spendai.insights.MonthTotal
import com.basic.spendai.insights.monthlyTotals
import com.basic.spendai.insights.WeeklyInsightsWorker
import com.basic.spendai.receipt.ReceiptPhotos
import com.basic.spendai.receipt.ReceiptScanner
import com.basic.spendai.sms.CategoryGuesser
import com.basic.spendai.sms.SmsImportRepository
import com.basic.spendai.sms.SmsInboxScanner
import com.basic.spendai.sms.SmsParser
import com.basic.spendai.stats.SpendAnswer
import com.basic.spendai.stats.SpendQueryExecutor
import com.basic.spendai.ui.home.InsightUi
import com.basic.spendai.ui.home.orderCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AiUiState(
    val enabled: Boolean = true,
    val status: AiStatus = AiStatus.Unavailable,
    /** Share of AI / rules suggestions the user kept unchanged over the last 30 days. */
    val aiAccuracy: AccuracyStats = AccuracyStats(0, 0),
    val rulesAccuracy: AccuracyStats = AccuracyStats(0, 0),
    /** The optional Gemma model, used when Gemini Nano isn't available. */
    val gemma: GemmaState = GemmaState.NotInstalled(),
)

data class ExpenseUiState(
    val expenses: List<Expense> = emptyList(),
    val total: Double = 0.0,
    val currency: AppCurrency = AppCurrency.default(),
    val pendingDetections: List<DetectedTransaction> = emptyList(),
    val smsImportEnabled: Boolean = false,
)

/**
 * Home's headline numbers: this calendar month so far, and last month up to the same day and time
 * (comparing against all of last month would make every early-month total look low).
 */
data class MonthSummary(
    val expenses: List<Expense> = emptyList(),
    val total: Double = 0.0,
    val lastMonthToDate: Double? = null,
)

/** [minutes] after midnight; [suggested] is null until there's enough history to suggest a time. */
data class ReminderUiState(
    val enabled: Boolean = false,
    val minutes: Int = DailyReminder.DEFAULT_MINUTES,
    val suggested: Int? = null,
)

/** One question and its answer on the Ask screen. */
data class AskEntry(
    val id: Long,
    val question: String,
    val sentence: String,
    val answer: SpendAnswer,
    /** The model wrote the sentence (the query and numbers always come from code). */
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

    val month: StateFlow<MonthSummary> =
        dao.observeAll().map { all -> monthSummary(all, java.time.ZonedDateTime.now()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthSummary())

    /** The last 6 calendar months, for the Insights trend chart. */
    val monthlyTotals: StateFlow<List<MonthTotal>> =
        dao.observeAll().map { monthlyTotals(it, java.time.Instant.now()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Payments that repeat about monthly, worked out live from all expenses. */
    val repeatingPayments: StateFlow<Finding.Subscriptions?> =
        dao.observeAll().map { InsightsEngine().repeatingPayments(it, java.time.Instant.now()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Categories most-used first, for the add/edit sheet's chips. */
    val categoryOrder: StateFlow<List<Category>> =
        dao.observeCategoryCounts()
            .map { counts -> orderCategories(counts.associate { it.category to it.count }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), orderCategories(emptyMap()))

    private val insights = InsightsRepository(application)

    /** The latest weekly summary for Home; null until one has been written (or for a week with no spending). */
    val weeklyInsight: StateFlow<InsightUi?> =
        combine(db.weeklyInsightDao().observeLatest(), settings.currency) { latest, currency ->
            val report = latest?.let(InsightsRepository::decode) ?: return@combine null
            val summary = latest.summary ?: return@combine null
            if (report.count == 0) return@combine null
            InsightUi(report, summary, latest.summaryFromAi, InsightWriter.facts(InsightWriter.Input(report, currency)).drop(1))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val insightsNotify: StateFlow<Boolean> =
        settings.insightsNotify.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        WeeklyInsightsWorker.schedule(application)
        GemmaModel.refresh(application)
    }

    private val budgetDao = db.budgetDao()

    /** Each budget against this calendar month's spending, in the order they were set. */
    val budgets: StateFlow<List<BudgetProgress>> =
        combine(dao.observeAll(), budgetDao.observeBudgets()) { all, budgets ->
            val now = java.time.Instant.now()
            val zone = java.time.ZoneId.systemDefault()
            budgets.map { BudgetMath.progress(all, it.category, it.monthlyLimit, now, zone) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val goals: StateFlow<List<SavingsGoal>> =
        budgetDao.observeGoals().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // A channel, not a SharedFlow: a nudge raised before the UI starts collecting is kept, not dropped.
    private val nudgeChannel = Channel<String>(Channel.BUFFERED)

    /** "You're at 85% of your Food budget…", once per category, level and month; shown as a snackbar. */
    val nudges: Flow<String> = nudgeChannel.receiveAsFlow()

    init {
        // Expenses are only added while the app is open, so watching here catches every crossing.
        viewModelScope.launch { budgets.collect(::checkNudges) }
    }

    private suspend fun checkNudges(progress: List<BudgetProgress>) {
        val month = java.time.YearMonth.now()
        budgetDao.pruneNudges(month.minusMonths(1).toString())
        val currency = settings.currency.first()
        progress.filter { it.level != BudgetLevel.OK }.forEach { p ->
            val fresh = budgetDao.recordNudge(BudgetNudge(p.category, month.toString(), p.level.name)) != -1L
            // Going straight past 100% also counts as having passed 80%.
            if (p.level == BudgetLevel.OVER) budgetDao.recordNudge(BudgetNudge(p.category, month.toString(), BudgetLevel.NEAR.name))
            if (fresh) BudgetNudges.message(p, currency)?.let { nudgeChannel.trySend(it) }
        }
    }

    fun setBudget(category: Category, limit: Double) {
        viewModelScope.launch { budgetDao.upsertBudget(Budget(category, limit)) }
    }

    fun removeBudget(category: Category) {
        viewModelScope.launch {
            db.withTransaction {
                budgetDao.deleteBudget(category)
                budgetDao.clearNudges(category)
            }
        }
    }

    /** The last 3 complete months' spending and a suggested budget, or null without enough history. */
    suspend fun suggestBudget(category: Category): CoachWriter.Topic.Suggestion? {
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        val expenses = dao.between(0, now.toEpochMilli() + 1)
        val suggested = BudgetMath.suggestBudget(expenses, category, now, zone) ?: return null
        return CoachWriter.Topic.Suggestion(category, suggested, BudgetMath.pastMonthTotals(expenses, category, now, zone))
    }

    /** What cutting [category] by [percent]% saves, from its average month; null with no history. */
    suspend fun whatIf(category: Category, percent: Int): CoachWriter.Topic.WhatIf? {
        val now = java.time.Instant.now()
        val zone = java.time.ZoneId.systemDefault()
        val average = BudgetMath.averageMonth(dao.between(0, now.toEpochMilli() + 1), category, now, zone)
        if (average <= 0) return null
        val (monthly, yearly) = BudgetMath.whatIf(average, percent)
        return CoachWriter.Topic.WhatIf(category, percent, average, monthly, yearly)
    }

    /** A coaching sentence; the model rewords computed facts, the template covers everything else. */
    suspend fun coach(topic: CoachWriter.Topic): AiResult<String> {
        val input = CoachWriter.Input(topic, settings.currency.first())
        return withContext(Dispatchers.Default) { CoachWriter.run(AiProviders.best(getApplication()), input) }
    }

    fun saveGoal(goal: SavingsGoal) {
        viewModelScope.launch { if (goal.id == 0L) budgetDao.insertGoal(goal) else budgetDao.updateGoal(goal) }
    }

    fun addToGoal(goal: SavingsGoal, amount: Double) {
        viewModelScope.launch { budgetDao.addToGoal(goal.id, amount) }
    }

    fun deleteGoal(goal: SavingsGoal) {
        viewModelScope.launch { budgetDao.deleteGoal(goal.id) }
    }

    /** On every resume: make this week's report if one is due, then write its summary while we're in front. */
    fun refreshInsights() {
        viewModelScope.launch {
            insights.ensureCurrent()
            insights.writeSummaryIfMissing(AiProviders.best(getApplication()), settings.currency.first())
        }
    }

    fun setInsightsNotify(enabled: Boolean) {
        viewModelScope.launch { settings.setInsightsNotify(enabled) }
    }

    /** The daily reminder, with a time suggested from when the user's spending usually ends. */
    val reminder: StateFlow<ReminderUiState> =
        combine(
            settings.reminderEnabled,
            settings.reminderMinutes,
            dao.observeAll().map { all ->
                DailyReminder.suggestMinutes(all.map { it.timestamp }, java.time.Instant.now(), java.time.ZoneId.systemDefault())
            },
        ) { enabled, minutes, suggested ->
            ReminderUiState(enabled, minutes ?: suggested ?: DailyReminder.DEFAULT_MINUTES, suggested)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReminderUiState())

    init {
        // Re-arms the alarm on every launch (a force-stop clears it).
        viewModelScope.launch { ReminderReceiver.reschedule(application) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // The first time on, keep the time the user was shown (the suggestion, or 9 PM).
            if (enabled && settings.reminderMinutes.first() == null) settings.setReminderMinutes(reminder.value.minutes)
            settings.setReminderEnabled(enabled)
            ReminderReceiver.reschedule(getApplication())
        }
    }

    fun setReminderTime(minutes: Int) {
        viewModelScope.launch {
            settings.setReminderMinutes(minutes)
            ReminderReceiver.reschedule(getApplication())
        }
    }

    val hiddenDetections: StateFlow<List<DetectedTransaction>> =
        detectedDao.observeHidden().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val aiStatus = MutableStateFlow<AiStatus>(AiStatus.Unavailable)
    private var aiDownload: Job? = null
    /** False until Nano's status is known, so Home doesn't flash the Gemma offer on Nano phones. */
    private val nanoChecked = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            aiStatus.value = AiProviders.nano.status()
            nanoChecked.value = true
        }
    }

    /**
     * Home's one-tap offer of the Gemma model: only on phones without Gemini Nano, when a download
     * is configured, until it's installed or the user says "Not now" (it stays while downloading).
     */
    val showGemmaOffer: StateFlow<Boolean> =
        combine(settings.aiEnabled, aiStatus, nanoChecked, GemmaModel.state, settings.gemmaOfferDismissed) { enabled, nano, checked, gemma, dismissed ->
            enabled && checked && nano == AiStatus.Unavailable && GemmaModel.canDownload &&
                (gemma is GemmaState.Copying || (gemma is GemmaState.NotInstalled && !dismissed))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun dismissGemmaOffer() {
        viewModelScope.launch { settings.dismissGemmaOffer() }
    }

    private val accuracySince = System.currentTimeMillis() - ACCURACY_WINDOW_MS

    val aiState: StateFlow<AiUiState> =
        combine(
            settings.aiEnabled,
            aiStatus,
            correctionDao.observeAccuracy(CorrectionSource.AI, accuracySince),
            correctionDao.observeAccuracy(CorrectionSource.RULES, accuracySince),
            GemmaModel.state,
        ) { enabled, status, ai, rules, gemma -> AiUiState(enabled, status, ai, rules, gemma) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiUiState())

    /** Re-checks whether Gemini Nano and Gemma are ready; cheap, so it runs whenever Settings opens. */
    fun refreshAiStatus() {
        GemmaModel.refresh(getApplication())
        if (aiDownload?.isActive == true) return
        viewModelScope.launch { aiStatus.value = AiProviders.nano.status() }
    }

    /** Gemma: download over Wi-Fi (when a download URL is configured). */
    fun downloadGemma() = GemmaModel.download(getApplication())

    /** Gemma: copy a .litertlm file the user picked. */
    fun importGemma(uri: Uri) = GemmaModel.import(getApplication(), uri)

    fun cancelGemma() = GemmaModel.cancel(getApplication())

    fun deleteGemma() {
        AiProviders.gemma(getApplication()).unload()
        GemmaModel.delete(getApplication())
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

    /** Question -> query (code, using the user's own expense titles) -> numbers (code) -> sentence (model or template). */
    fun ask(question: String) {
        if (_asking.value) return
        _asking.value = true
        viewModelScope.launch {
            try {
                val provider = AiProviders.best(getApplication())
                val titles = dao.between(0, System.currentTimeMillis() + DAY_MS).map { it.title }
                val query = withContext(Dispatchers.Default) { SpendQueryTranslator.translate(question, titles) }
                val answer = SpendQueryExecutor(dao::between).run(query)
                val input = AnswerWriter.Input(question, answer, settings.currency.first())
                val sentence = withContext(Dispatchers.Default) { AnswerWriter.run(provider, input) }
                val entry = AskEntry(System.nanoTime(), question, sentence.value, answer, sentence.fromAi)
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

    /** Saves changes made in the edit sheet; keeps the expense's id. */
    fun updateExpense(expense: Expense) {
        viewModelScope.launch { dao.update(expense) }
    }

    /** Puts a just-deleted expense back with its original id and date (the "Undo" after a swipe). */
    fun restoreExpense(expense: Expense) {
        viewModelScope.launch { dao.insert(expense) }
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

internal fun monthSummary(all: List<Expense>, now: java.time.ZonedDateTime): MonthSummary {
    val zone = now.zone
    val monthStart = now.toLocalDate().withDayOfMonth(1).atStartOfDay(zone)
    val lastMonthStart = monthStart.minusMonths(1)
    // Same moment last month (clamped by java.time for short months, e.g. Mar 31 -> Feb 28).
    val lastMonthSameTime = now.minusMonths(1)
    val thisMonth = all.filter { it.timestamp >= monthStart.toInstant().toEpochMilli() && it.timestamp <= now.toInstant().toEpochMilli() }
    val lastMonth = all.filter {
        it.timestamp >= lastMonthStart.toInstant().toEpochMilli() && it.timestamp <= lastMonthSameTime.toInstant().toEpochMilli()
    }
    val hasHistory = all.any { it.timestamp < monthStart.toInstant().toEpochMilli() }
    return MonthSummary(thisMonth, thisMonth.sumOf { it.amount }, if (hasHistory) lastMonth.sumOf { it.amount } else null)
}

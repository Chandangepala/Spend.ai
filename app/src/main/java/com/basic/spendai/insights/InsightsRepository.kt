package com.basic.spendai.insights

import android.content.Context
import com.basic.spendai.ai.AiProvider
import com.basic.spendai.ai.InsightWriter
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.ExpenseDatabase
import com.basic.spendai.data.WeeklyInsight
import kotlinx.serialization.json.Json
import java.time.Instant

/** Creates one report a week and fills in its summary. Shared by the weekly worker and the app. */
class InsightsRepository(context: Context) {
    private val db = ExpenseDatabase.get(context)
    private val insightDao = db.weeklyInsightDao()
    private val expenseDao = db.expenseDao()

    /** Builds a new report when the latest is a week old (or missing). Returns it if one was created. */
    suspend fun ensureCurrent(now: Instant = Instant.now()): WeeklyReport? {
        val latest = insightDao.latest()
        if (latest != null && now.toEpochMilli() - latest.createdAt < WEEK_MS) return null
        // The rules look back up to ~4 months (subscriptions, "usual" weeks, typical payment sizes).
        val expenses = expenseDao.between(now.toEpochMilli() - LOOKBACK_MS, now.toEpochMilli() + DAY_MS)
        val report = InsightsEngine().analyze(expenses, now)
        insightDao.upsert(WeeklyInsight(report.from, report.until, now.toEpochMilli(), json.encodeToString(report), null, false))
        insightDao.prune(KEEP_WEEKS)
        return report
    }

    /**
     * Writes the summary for the latest report if it has none. Call from the foreground: Gemini Nano
     * refuses background calls, and the template covers phones without it.
     */
    suspend fun writeSummaryIfMissing(provider: AiProvider, currency: AppCurrency) {
        val latest = insightDao.latest() ?: return
        if (latest.summary != null) return
        val report = decode(latest) ?: return
        val result = InsightWriter.run(provider, InsightWriter.Input(report, currency))
        insightDao.setSummary(latest.weekFrom, result.value, result.fromAi)
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true }

        fun decode(insight: WeeklyInsight): WeeklyReport? =
            runCatching { json.decodeFromString<WeeklyReport>(insight.reportJson) }.getOrNull()

        private const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val WEEK_MS = 7 * DAY_MS
        private const val LOOKBACK_MS = 130 * DAY_MS
        private const val KEEP_WEEKS = 12
    }
}

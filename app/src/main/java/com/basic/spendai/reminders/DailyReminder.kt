package com.basic.spendai.reminders

import com.basic.spendai.budgets.BudgetLevel
import com.basic.spendai.budgets.BudgetNudges
import com.basic.spendai.budgets.BudgetProgress
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.util.formatCurrency
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where tapping the reminder takes the user. */
enum class ReminderTarget { ADD, REVIEW }

data class ReminderMessage(val title: String, val text: String, val target: ReminderTarget)

/**
 * The daily reminder's decisions, in plain code: what to say (from the user's own numbers),
 * whether to say anything at all, and a good time to suggest.
 */
object DailyReminder {
    /** 9 PM, used until there's enough history to suggest a time. */
    const val DEFAULT_MINUTES = 21 * 60

    private const val EARLIEST_SUGGESTION = 18 * 60
    private const val LATEST_SUGGESTION = 22 * 60 + 30
    private const val MIN_DAYS = 5
    private const val LOOKBACK_DAYS = 30L
    private const val AFTER_LAST_SPEND = 30
    private const val STEP = 15

    /**
     * Null on days there's nothing to do: something was logged today and no SMS spends are waiting.
     * Otherwise the most useful prompt, plus the tightest budget if one is near or over.
     */
    fun message(
        loggedToday: Int,
        pendingCount: Int,
        pendingTotal: Double,
        budgets: List<BudgetProgress>,
        currency: AppCurrency,
    ): ReminderMessage? {
        val (title, text, target) = when {
            pendingCount > 0 -> Triple(
                if (pendingCount == 1) "1 spend to review" else "$pendingCount spends to review",
                "${formatCurrency(pendingTotal, currency)} from your bank SMS, waiting for you to confirm.",
                ReminderTarget.REVIEW,
            )
            loggedToday == 0 -> Triple(
                "Add today's spending?",
                "Nothing logged today yet. One line does it, like \"lunch 250, auto 80\".",
                ReminderTarget.ADD,
            )
            else -> return null
        }
        val budgetLine = budgets.filter { it.level != BudgetLevel.OK }.maxByOrNull { it.fraction }
            ?.let { BudgetNudges.message(it, currency) }
        return ReminderMessage(title, listOfNotNull(text, budgetLine).joinToString(" "), target)
    }

    /**
     * A reminder time (minutes after midnight) shortly after the user's spending usually ends for
     * the day: the median of each day's last expense over the past 30 days, plus 30 minutes, rounded
     * up to a quarter hour and kept in the evening. Null with fewer than 5 days of history.
     */
    fun suggestMinutes(timestamps: List<Long>, now: Instant, zone: ZoneId): Int? {
        val since = now.minus(Duration.ofDays(LOOKBACK_DAYS))
        val lastPerDay = timestamps
            .map(Instant::ofEpochMilli)
            .filter { !it.isBefore(since) && !it.isAfter(now) }
            .map { it.atZone(zone) }
            // A bare date (no time of day) says nothing about when the user spends.
            .filter { it.toLocalTime() != LocalTime.MIDNIGHT }
            .groupBy { it.toLocalDate() }
            .values
            .map { day -> day.maxOf { it.toLocalTime().toSecondOfDay() / 60 } }
        if (lastPerDay.size < MIN_DAYS) return null
        val median = lastPerDay.sorted()[lastPerDay.size / 2]
        val rounded = (median + AFTER_LAST_SPEND + STEP - 1) / STEP * STEP
        return rounded.coerceIn(EARLIEST_SUGGESTION, LATEST_SUGGESTION)
    }

    /** The next time the clock shows [minutes] after midnight: later today, or else tomorrow. */
    fun nextTrigger(minutes: Int, now: ZonedDateTime): ZonedDateTime {
        val time = LocalTime.of(minutes / 60, minutes % 60)
        val today = now.toLocalDate().atTime(time).atZone(now.zone)
        return if (today.isAfter(now)) today else now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
    }
}

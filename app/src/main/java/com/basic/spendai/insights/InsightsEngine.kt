package com.basic.spendai.insights

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/** Something worth telling the user about their week. All numbers are computed here, never by a model. */
@Serializable
sealed interface Finding {
    /** A category well above (or below) its usual week, based on the previous 12 weeks. */
    @Serializable
    @SerialName("category")
    data class CategoryVsUsual(val category: Category, val thisWeek: Double, val usualWeek: Double, val percent: Int) : Finding

    /** Over the last 4 weeks, spending per weekend day vs. per weekday. */
    @Serializable
    @SerialName("weekend")
    data class WeekendSpike(val weekendPerDay: Double, val weekdayPerDay: Double, val ratio: Double) : Finding

    /** Same merchant, similar amount, about a month apart: probably a subscription or standing payment. */
    @Serializable
    @SerialName("subscriptions")
    data class Subscriptions(val items: List<Subscription>, val monthlyTotal: Double) : Finding

    /** A payment much bigger than usual for its category. */
    @Serializable
    @SerialName("large")
    data class LargeSpend(val title: String, val amount: Double, val category: Category, val typical: Double) : Finding
}

@Serializable
data class Subscription(val merchant: String, val amount: Double)

/** The week being summarised: [from] (inclusive) to [until] (exclusive), in epoch millis. */
@Serializable
data class WeeklyReport(
    val from: Long,
    val until: Long,
    val total: Double,
    val count: Int,
    val topCategory: Category?,
    val findings: List<Finding>,
)

/** Finds patterns in expenses. Pure Kotlin so every rule is unit-tested with a fixed clock. */
class InsightsEngine(private val zone: ZoneId = ZoneId.systemDefault()) {

    fun analyze(expenses: List<Expense>, now: Instant): WeeklyReport {
        val today = now.atZone(zone).toLocalDate()
        fun ms(daysAgo: Long) = today.minusDays(daysAgo).atStartOfDay(zone).toInstant().toEpochMilli()
        val weekFrom = ms(6)
        val weekUntil = ms(-1)
        val week = expenses.filter { it.timestamp in weekFrom until weekUntil }

        val findings = buildList {
            addAll(categoryVsUsual(week, expenses.filter { it.timestamp in ms(6 + HISTORY_DAYS) until weekFrom }, expenses))
            weekendSpike(expenses.filter { it.timestamp in ms(27) until weekUntil })?.let(::add)
            subscriptions(expenses.filter { it.timestamp in ms(SUBSCRIPTION_LOOKBACK_DAYS) until weekUntil }, now)?.let(::add)
            addAll(largeSpends(week, expenses.filter { it.timestamp in ms(6 + LARGE_LOOKBACK_DAYS) until weekFrom }))
        }
        return WeeklyReport(
            from = weekFrom,
            until = weekUntil,
            total = week.sumOf { it.amount },
            count = week.size,
            topCategory = week.groupBy { it.category }.maxByOrNull { (_, list) -> list.sumOf { it.amount } }?.key,
            findings = findings,
        )
    }

    /** Up to 2 categories at least 30% above their usual week (and 1 well below), when there's enough history. */
    private fun categoryVsUsual(week: List<Expense>, history: List<Expense>, all: List<Expense>): List<Finding> {
        // Need 3+ weeks of history before "usual" means anything.
        val oldest = all.minOfOrNull { it.timestamp } ?: return emptyList()
        val weekStart = week.minOfOrNull { it.timestamp } ?: return emptyList()
        if (weekStart - oldest < MIN_HISTORY_DAYS * DAY_MS) return emptyList()

        val historyWeeks = (HISTORY_DAYS / 7.0)
        val weekTotal = week.sumOf { it.amount }
        val thisWeek = week.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val usual = history.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } / historyWeeks }

        val above = thisWeek.mapNotNull { (category, spent) ->
            val typical = usual[category] ?: return@mapNotNull null
            // Ignore tiny categories: the extra must be a noticeable share of the week.
            if (spent < typical * 1.3 || spent - typical < weekTotal * 0.05) return@mapNotNull null
            Finding.CategoryVsUsual(category, spent, typical, pct(spent, typical))
        }.sortedByDescending { it.thisWeek - it.usualWeek }.take(2)

        val below = usual.filter { (_, typical) -> typical >= weekTotal * 0.1 }.mapNotNull { (category, typical) ->
            val spent = thisWeek[category] ?: 0.0
            if (spent > typical * 0.7) null else Finding.CategoryVsUsual(category, spent, typical, pct(spent, typical))
        }.maxByOrNull { it.usualWeek - it.thisWeek }

        return above + listOfNotNull(below)
    }

    private fun weekendSpike(last4Weeks: List<Expense>): Finding.WeekendSpike? {
        if (last4Weeks.isEmpty()) return null
        val (weekend, weekday) = last4Weeks.partition {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
        // 28 days = 8 weekend days + 20 weekdays.
        val weekendPerDay = weekend.sumOf { it.amount } / 8
        val weekdayPerDay = weekday.sumOf { it.amount } / 20
        if (weekdayPerDay <= 0 || weekendPerDay < weekdayPerDay * 1.5) return null
        return Finding.WeekendSpike(weekendPerDay, weekdayPerDay, (weekendPerDay / weekdayPerDay * 10).roundToInt() / 10.0)
    }

    /** Repeating payments over the last 4 months, for the Insights tab (null when none). */
    fun repeatingPayments(expenses: List<Expense>, now: Instant): Finding.Subscriptions? {
        val from = now.atZone(zone).toLocalDate().minusDays(SUBSCRIPTION_LOOKBACK_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        return subscriptions(expenses.filter { it.timestamp in from..now.toEpochMilli() }, now)
    }

    /** Charges from the same merchant 25–35 days apart with amounts within 10%, still active in the last 40 days. */
    private fun subscriptions(recent: List<Expense>, now: Instant): Finding.Subscriptions? {
        val items = recent.groupBy { it.title.trim().lowercase() }.mapNotNull { (_, charges) ->
            if (charges.size < 2) return@mapNotNull null
            val sorted = charges.sortedBy { it.timestamp }
            val latest = sorted.last()
            if (now.toEpochMilli() - latest.timestamp > 40 * DAY_MS) return@mapNotNull null
            val monthly = sorted.zipWithNext().any { (a, b) ->
                (b.timestamp - a.timestamp) / DAY_MS in 25..35 && abs(b.amount - a.amount) <= a.amount * 0.10
            }
            if (monthly) Subscription(latest.title.trim(), latest.amount) else null
        }.sortedByDescending { it.amount }
        if (items.isEmpty()) return null
        return Finding.Subscriptions(items, items.sumOf { it.amount })
    }

    /** Up to 2 payments over 3× their category's median from the previous 90 days (needs 3+ past payments). */
    private fun largeSpends(week: List<Expense>, history: List<Expense>): List<Finding> {
        val medians = history.groupBy { it.category }.filterValues { it.size >= 3 }.mapValues { (_, l) -> median(l.map { it.amount }) }
        return week.mapNotNull { e ->
            val typical = medians[e.category] ?: return@mapNotNull null
            if (typical <= 0 || e.amount < typical * 3) null else Finding.LargeSpend(e.title, e.amount, e.category, typical)
        }.sortedByDescending { it.amount }.take(2)
    }

    private fun median(values: List<Double>): Double = values.sorted().let { s ->
        if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    private fun pct(now: Double, usual: Double) = if (usual == 0.0) 0 else ((now - usual) / usual * 100).roundToInt()

    companion object {
        const val DAY_MS = 24 * 60 * 60 * 1000L
        private const val HISTORY_DAYS = 84L // 12 weeks
        private const val MIN_HISTORY_DAYS = 21L
        private const val SUBSCRIPTION_LOOKBACK_DAYS = 120L
        private const val LARGE_LOOKBACK_DAYS = 90L
    }
}

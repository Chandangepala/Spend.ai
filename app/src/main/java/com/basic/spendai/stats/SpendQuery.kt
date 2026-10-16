package com.basic.spendai.stats

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class Metric { TOTAL, COUNT, AVERAGE, TOP_MERCHANTS }

enum class Period(val label: String) {
    TODAY("Today"),
    YESTERDAY("Yesterday"),
    THIS_WEEK("This week"),
    LAST_WEEK("Last week"),
    THIS_MONTH("This month"),
    LAST_MONTH("Last month"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year"),
    ALL_TIME("All time");

    /** [start, end) in epoch millis for [now] in [zone]. Weeks start on Monday. */
    fun range(now: Instant, zone: ZoneId): LongRange {
        val today = now.atZone(zone).toLocalDate()
        fun ms(date: LocalDate) = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val (from, until) = when (this) {
            TODAY -> ms(today) to ms(today.plusDays(1))
            YESTERDAY -> ms(today.minusDays(1)) to ms(today)
            THIS_WEEK -> ms(monday) to ms(today.plusDays(1))
            LAST_WEEK -> ms(monday.minusWeeks(1)) to ms(monday)
            THIS_MONTH -> ms(today.withDayOfMonth(1)) to ms(today.plusDays(1))
            LAST_MONTH -> ms(today.withDayOfMonth(1).minusMonths(1)) to ms(today.withDayOfMonth(1))
            LAST_7_DAYS -> ms(today.minusDays(6)) to ms(today.plusDays(1))
            LAST_30_DAYS -> ms(today.minusDays(29)) to ms(today.plusDays(1))
            THIS_YEAR -> ms(today.withDayOfYear(1)) to ms(today.plusDays(1))
            ALL_TIME -> 0L to ms(today.plusDays(1))
        }
        return from until until
    }
}

/** A question about spending, reduced to something code can answer exactly. */
data class SpendQuery(
    val metric: Metric = Metric.TOTAL,
    /** Empty means every category. */
    val categories: Set<Category> = emptySet(),
    /** Title keywords, e.g. ["swiggy", "zomato"] for "food delivery"; empty means any. */
    val merchantContains: List<String> = emptyList(),
    val period: Period = Period.THIS_MONTH,
    val compareTo: Period? = null,
) {
    /** "Food · Swiggy, Zomato", or null when the question covers all spending. */
    fun scope(): String? = buildList {
        if (categories.isNotEmpty()) add(categories.joinToString(", ") { it.label })
        if (merchantContains.isNotEmpty()) add(merchantContains.joinToString(", ") { it.replaceFirstChar(Char::uppercase) })
    }.joinToString(" · ").ifEmpty { null }

    /** "Food · Last month vs This month", shown so users can see what was actually answered. */
    fun describe(): String =
        listOfNotNull(scope(), if (compareTo != null) "${period.label} vs ${compareTo.label}" else period.label).joinToString(" · ")
}

data class PeriodStats(
    val period: Period,
    val total: Double,
    val count: Int,
    val topMerchants: List<Pair<String, Double>>,
) {
    val average: Double get() = if (count == 0) 0.0 else total / count
}

data class SpendAnswer(val query: SpendQuery, val primary: PeriodStats, val comparison: PeriodStats?)

/** Runs a [SpendQuery] over expenses. Every number shown to the user comes from here, never the model. */
class SpendQueryExecutor(
    private val expensesBetween: suspend (from: Long, until: Long) -> List<Expense>,
    private val now: () -> Instant = Instant::now,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    suspend fun run(query: SpendQuery): SpendAnswer {
        val instant = now()
        return SpendAnswer(
            query = query,
            primary = stats(query, query.period, instant),
            comparison = query.compareTo?.let { stats(query, it, instant) },
        )
    }

    private suspend fun stats(query: SpendQuery, period: Period, instant: Instant): PeriodStats {
        val range = period.range(instant, zone)
        val matching = expensesBetween(range.first, range.last + 1).filter { e ->
            (query.categories.isEmpty() || e.category in query.categories) &&
                (query.merchantContains.isEmpty() || query.merchantContains.any { e.title.contains(it, ignoreCase = true) })
        }
        val top = matching.groupBy { it.title.trim() }
            .map { (title, items) -> title to items.sumOf { it.amount } }
            .sortedByDescending { it.second }
            .take(TOP_MERCHANTS)
        return PeriodStats(period, matching.sumOf { it.amount }, matching.size, top)
    }

    private companion object {
        const val TOP_MERCHANTS = 3
    }
}

package com.basic.spendai.insights

import com.basic.spendai.data.Expense
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** Total spent in one calendar month; [partial] marks the current month (still in progress). */
data class MonthTotal(val month: YearMonth, val total: Double, val count: Int, val partial: Boolean)

/** The last [months] calendar months up to and including the current one, oldest first; empty months are 0. */
fun monthlyTotals(expenses: List<Expense>, now: Instant, zone: ZoneId = ZoneId.systemDefault(), months: Int = 6): List<MonthTotal> {
    val current = YearMonth.from(now.atZone(zone))
    val byMonth = expenses.groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }
    return (months - 1 downTo 0).map { back ->
        val month = current.minusMonths(back.toLong())
        val items = byMonth[month].orEmpty()
        MonthTotal(month, items.sumOf { it.amount }, items.size, partial = back == 0)
    }
}

/** A clean axis maximum at or above [max]: 1, 2, 2.5 or 5 × a power of ten (e.g. 8,919 -> 10,000). */
fun niceCeiling(max: Double): Double {
    if (max <= 0) return 1.0
    val magnitude = Math.pow(10.0, Math.floor(Math.log10(max)))
    return listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= max }
}

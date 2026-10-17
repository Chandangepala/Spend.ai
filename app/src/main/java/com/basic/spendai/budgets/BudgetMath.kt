package com.basic.spendai.budgets

import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.util.formatCurrency
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Where a budget stands this month. Every number here is computed by code. */
enum class BudgetLevel { OK, NEAR, OVER }

data class BudgetProgress(
    val category: Category,
    val limit: Double,
    val spent: Double,
    /** Days left in the month after today (0 on the last day). */
    val daysLeft: Int,
) {
    val fraction: Double get() = if (limit <= 0) 0.0 else spent / limit
    val percent: Int get() = (fraction * 100).roundToLong().toInt()
    val level: BudgetLevel get() = when {
        fraction >= 1.0 -> BudgetLevel.OVER
        fraction >= NEAR_AT -> BudgetLevel.NEAR
        else -> BudgetLevel.OK
    }
    val remaining: Double get() = limit - spent

    companion object {
        const val NEAR_AT = 0.8
    }
}

object BudgetMath {

    /** Spending per category in each of the last [months] *complete* calendar months, oldest first. */
    fun pastMonthTotals(expenses: List<Expense>, category: Category, now: Instant, zone: ZoneId, months: Int = 3): List<Double> {
        val current = YearMonth.from(now.atZone(zone))
        val byMonth = expenses.filter { it.category == category }
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) }
        return (months downTo 1).map { back -> byMonth[current.minusMonths(back.toLong())].orEmpty().sumOf { it.amount } }
    }

    /**
     * A realistic monthly budget: the median of the last 3 complete months, rounded (to 50 under
     * ₹1,000, 100 under ₹10,000, else 500). Null without 2+ months of history, or if nothing was spent.
     */
    fun suggestBudget(expenses: List<Expense>, category: Category, now: Instant, zone: ZoneId): Double? {
        val firstMonth = expenses.minOfOrNull { it.timestamp }?.let { YearMonth.from(Instant.ofEpochMilli(it).atZone(zone)) } ?: return null
        val current = YearMonth.from(now.atZone(zone))
        if (firstMonth > current.minusMonths(2)) return null // fewer than 2 complete months of history
        val totals = pastMonthTotals(expenses, category, now, zone).sorted()
        val median = totals[totals.size / 2]
        if (median <= 0) return null
        val step = when {
            median < 1_000 -> 50.0
            median < 10_000 -> 100.0
            else -> 500.0
        }
        return (median / step).roundToLong() * step
    }

    /** This calendar month's spending against [limit]. */
    fun progress(expenses: List<Expense>, category: Category, limit: Double, now: Instant, zone: ZoneId): BudgetProgress {
        val today = now.atZone(zone).toLocalDate()
        val month = YearMonth.from(today)
        val spent = expenses.filter {
            it.category == category && YearMonth.from(Instant.ofEpochMilli(it.timestamp).atZone(zone)) == month &&
                it.timestamp <= now.toEpochMilli()
        }.sumOf { it.amount }
        return BudgetProgress(category, limit, spent, month.lengthOfMonth() - today.dayOfMonth)
    }

    /** Average month over the last 3 complete months that had any spending in [category]; 0 if none. */
    fun averageMonth(expenses: List<Expense>, category: Category, now: Instant, zone: ZoneId): Double {
        val withSpend = pastMonthTotals(expenses, category, now, zone).filter { it > 0 }
        return if (withSpend.isEmpty()) 0.0 else withSpend.average()
    }

    /** "Cut X by [percent]%": the monthly and yearly saving from the average month. */
    fun whatIf(averageMonth: Double, percent: Int): Pair<Double, Double> {
        val monthly = averageMonth * percent / 100.0
        return monthly to monthly * 12
    }
}

/** A savings goal's numbers: how far along, and what it takes per month to finish by its date. */
data class GoalPlan(val saved: Double, val target: Double, val monthsLeft: Int?, val perMonth: Double?) {
    val fraction: Double get() = if (target <= 0) 0.0 else (saved / target).coerceIn(0.0, 1.0)
    val remaining: Double get() = (target - saved).coerceAtLeast(0.0)
    val done: Boolean get() = saved >= target
}

object GoalMath {
    /** Months left counts any part-month, minimum 1 (a goal due this month still needs this month). */
    fun plan(saved: Double, target: Double, deadline: LocalDate?, today: LocalDate): GoalPlan {
        val remaining = (target - saved).coerceAtLeast(0.0)
        if (deadline == null || remaining == 0.0) return GoalPlan(saved, target, null, null)
        val days = ChronoUnit.DAYS.between(today, deadline).coerceAtLeast(0)
        val months = ceil(days / 30.4375).toInt().coerceAtLeast(1)
        return GoalPlan(saved, target, months, remaining / months)
    }
}

object BudgetNudges {
    /** "You're at 85% of your Dining & Drinks budget with 10 days left." All numbers are computed. */
    fun message(p: BudgetProgress, currency: AppCurrency): String? {
        val days = when (p.daysLeft) {
            0 -> "on the last day of the month"
            1 -> "with 1 day left"
            else -> "with ${p.daysLeft} days left"
        }
        return when (p.level) {
            BudgetLevel.OK -> null
            BudgetLevel.NEAR -> "You're at ${p.percent}% of your ${p.category.label} budget $days."
            BudgetLevel.OVER ->
                "${p.category.label} is over budget by ${formatCurrency(-p.remaining, currency)} $days."
        }
    }
}

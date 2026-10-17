package com.basic.spendai.budgets

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BudgetMathTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 21, 10, 0).atZone(zone).toInstant() // 10 days left in October
    private fun e(amount: Double, m: Int, d: Int, c: Category = Category.Food, y: Int = 2026) =
        Expense(0, "x", amount, c, LocalDateTime.of(y, m, d, 12, 0).atZone(zone).toInstant().toEpochMilli())

    private val history = listOf(
        e(5800.0, 7, 10), e(6400.0, 8, 12), e(6100.0, 9, 5), // Food: Jul–Sep
        e(5100.0, 10, 3), e(9000.0, 10, 4, Category.Home),
    )

    @Test
    fun suggestionIsTheRoundedMedianOfTheLastThreeMonths() {
        assertEquals(6100.0, BudgetMath.suggestBudget(history, Category.Food, now, zone)!!, 0.001)
        val small = listOf(e(230.0, 7, 1, Category.Health), e(410.0, 8, 1, Category.Health), e(380.0, 9, 1, Category.Health))
        assertEquals(400.0, BudgetMath.suggestBudget(small, Category.Health, now, zone)!!, 0.001) // 380 -> nearest 50
        assertEquals(12_500.0, BudgetMath.suggestBudget(listOf(e(12_340.0, 7, 1), e(12_600.0, 8, 1), e(13_100.0, 9, 1)), Category.Food, now, zone)!!, 0.001)
    }

    @Test
    fun noSuggestionWithoutHistory() {
        // Data only since Oct 1 (like a new user): nothing to base a suggestion on.
        assertNull(BudgetMath.suggestBudget(listOf(e(5100.0, 10, 3)), Category.Food, now, zone))
        assertNull(BudgetMath.suggestBudget(history, Category.Travel, now, zone)) // never spent
    }

    @Test
    fun progressLevelsAndDaysLeft() {
        val p = BudgetMath.progress(history, Category.Food, 6000.0, now, zone)
        assertEquals(5100.0, p.spent, 0.001)
        assertEquals(85, p.percent)
        assertEquals(BudgetLevel.NEAR, p.level)
        assertEquals(10, p.daysLeft)
        assertEquals(BudgetLevel.OK, BudgetMath.progress(history, Category.Food, 10_000.0, now, zone).level)
        assertEquals(BudgetLevel.OVER, BudgetMath.progress(history, Category.Food, 5_000.0, now, zone).level)
        assertEquals(BudgetLevel.OVER, BudgetProgress(Category.Food, 100.0, 100.0, 0).level) // exactly 100% is over
    }

    @Test
    fun whatIfUsesTheAverageMonth() {
        val avg = BudgetMath.averageMonth(history, Category.Food, now, zone)
        assertEquals(6100.0, avg, 0.001)
        val (monthly, yearly) = BudgetMath.whatIf(avg, 30)
        assertEquals(1830.0, monthly, 0.001)
        assertEquals(21_960.0, yearly, 0.001)
        assertEquals(0.0, BudgetMath.averageMonth(history, Category.Travel, now, zone), 0.001)
    }

    @Test
    fun goalPlanPerMonth() {
        val today = LocalDate.of(2026, 10, 21)
        val plan = GoalMath.plan(saved = 12_000.0, target = 30_000.0, deadline = LocalDate.of(2027, 3, 31), today = today)
        assertEquals(6, plan.monthsLeft)
        assertEquals(3000.0, plan.perMonth!!, 0.001)
        assertEquals(0.4, plan.fraction, 0.001)
        assertNull(GoalMath.plan(5000.0, 30_000.0, null, today).perMonth) // no date, no per-month figure
        assertEquals(1, GoalMath.plan(0.0, 900.0, today.plusDays(3), today).monthsLeft) // due this month
        assertTrue(GoalMath.plan(31_000.0, 30_000.0, today.plusDays(90), today).done)
    }
}

class BudgetNudgesTest {
    @Test
    fun messages() {
        val inr = com.basic.spendai.data.AppCurrency.INR
        assertEquals(
            "You're at 85% of your Dining & Drinks budget with 10 days left.",
            BudgetNudges.message(BudgetProgress(Category.Food, 6000.0, 5100.0, 10), inr),
        )
        assertEquals(
            "Dining & Drinks is over budget by ₹300.00 with 1 day left.",
            BudgetNudges.message(BudgetProgress(Category.Food, 6000.0, 6300.0, 1), inr),
        )
        assertNull(BudgetNudges.message(BudgetProgress(Category.Food, 6000.0, 100.0, 1), inr))
    }
}

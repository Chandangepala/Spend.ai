package com.basic.spendai.ui

import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.activity.dayLabel
import com.basic.spendai.ui.home.monthComparison
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HomeNumbersTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDateTime.of(y, m, d, h, 0).atZone(zone)
    private fun e(amount: Double, y: Int, m: Int, d: Int, h: Int = 12) =
        Expense(0, "x", amount, Category.Other, at(y, m, d, h).toInstant().toEpochMilli())

    @Test
    fun monthToDateComparesTheSamePointOfLastMonth() {
        val now = at(2026, 10, 17, 10)
        val all = listOf(
            e(100.0, 2026, 10, 1), e(200.0, 2026, 10, 17, 9), e(999.0, 2026, 10, 17, 11), // last one is in the future
            e(50.0, 2026, 9, 1), e(70.0, 2026, 9, 17, 9), e(800.0, 2026, 9, 20), // Sep 20 is after "this time last month"
        )
        val summary = monthSummary(all, now)
        assertEquals(300.0, summary.total, 0.001)
        assertEquals(2, summary.expenses.size)
        assertEquals(120.0, summary.lastMonthToDate!!, 0.001)
    }

    @Test
    fun shortMonthsClampToTheirLastDay() {
        // Mar 31 compares with Feb 1–28 (2026 isn't a leap year).
        val summary = monthSummary(listOf(e(10.0, 2026, 2, 28, 9), e(5.0, 2026, 1, 31)), at(2026, 3, 31, 10))
        assertEquals(10.0, summary.lastMonthToDate!!, 0.001)
    }

    @Test
    fun noHistoryMeansNoComparison() {
        assertNull(monthSummary(listOf(e(10.0, 2026, 10, 2)), at(2026, 10, 17)).lastMonthToDate)
    }

    @Test
    fun comparisonText() {
        assertEquals("₹1,200.00 less than this time last month", monthComparison(3800.0, 5000.0, AppCurrency.INR))
        assertEquals("₹300.00 more than this time last month", monthComparison(800.0, 500.0, AppCurrency.INR))
        assertEquals("Same as this time last month", monthComparison(500.0, 500.0, AppCurrency.INR))
        assertNull(monthComparison(0.0, 0.0, AppCurrency.INR))
    }

    @Test
    fun dayHeaders() {
        val today = LocalDate.of(2026, 10, 17)
        assertEquals("Today", dayLabel(today, today))
        assertEquals("Yesterday", dayLabel(today.minusDays(1), today))
        assertEquals("Wed, Oct 14", dayLabel(LocalDate.of(2026, 10, 14), today).replace(" ", " "))
        assertEquals("Wed, Dec 31, 2025", dayLabel(LocalDate.of(2025, 12, 31), today))
    }
}

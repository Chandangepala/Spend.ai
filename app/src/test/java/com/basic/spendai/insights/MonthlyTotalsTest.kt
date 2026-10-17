package com.basic.spendai.insights

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class MonthlyTotalsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 17, 10, 0).atZone(zone).toInstant()
    private fun e(amount: Double, m: Int, d: Int, h: Int = 12, y: Int = 2026) =
        Expense(0, "x", amount, Category.Other, LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli())

    @Test
    fun sixMonthsOldestFirstWithEmptyMonthsAsZero() {
        val totals = monthlyTotals(listOf(e(100.0, 10, 1), e(50.0, 10, 17, 9), e(300.0, 8, 31, 23), e(999.0, 4, 30)), now, zone)
        assertEquals((5..10).map { YearMonth.of(2026, it) }, totals.map { it.month })
        assertEquals(listOf(0.0, 0.0, 0.0, 300.0, 0.0, 150.0), totals.map { it.total })
        assertTrue(totals.last().partial)
        assertEquals(listOf(false, false, false, false, false, true), totals.map { it.partial })
    }

    @Test
    fun monthBoundaryUsesLocalTime() {
        // 00:30 IST on Oct 1 is still Sep 30 in UTC; it belongs to October here.
        val totals = monthlyTotals(listOf(e(40.0, 10, 1, 0)), now, zone)
        assertEquals(40.0, totals.last().total, 0.001)
    }

    @Test
    fun crossesTheYear() {
        val feb = LocalDateTime.of(2027, 2, 10, 10, 0).atZone(zone).toInstant()
        assertEquals(YearMonth.of(2026, 9), monthlyTotals(emptyList(), feb, zone).first().month)
    }

    @Test
    fun niceAxisMaximum() {
        assertEquals(10000.0, niceCeiling(8919.22), 0.001)
        assertEquals(2500.0, niceCeiling(2100.0), 0.001)
        assertEquals(500.0, niceCeiling(500.0), 0.001)
        assertEquals(1.0, niceCeiling(0.0), 0.001)
    }
}

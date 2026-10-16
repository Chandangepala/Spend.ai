package com.basic.spendai.stats

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class SpendQueryExecutorTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    // Friday 17 October 2026, 10:00 IST.
    private val now = LocalDateTime.of(2026, 10, 17, 10, 0).atZone(zone).toInstant()

    private fun at(month: Int, day: Int, hour: Int = 12) =
        LocalDateTime.of(2026, month, day, hour, 0).atZone(zone).toInstant().toEpochMilli()

    private val expenses = listOf(
        Expense(1, "Swiggy", 300.0, Category.Food, at(10, 16)),
        Expense(2, "Zomato", 200.0, Category.Food, at(10, 12, 0)), // Monday, first hour of this week
        Expense(3, "Uber", 150.0, Category.Transport, at(10, 11, 23)), // Sunday: last week
        Expense(4, "Swiggy", 400.0, Category.Food, at(9, 20)),
        Expense(5, "Lunch", 100.0, Category.Food, at(9, 1, 0)),
        Expense(6, "Rent", 9000.0, Category.Bills, at(8, 31, 23)), // August: outside last month
    )

    private val executor = SpendQueryExecutor(
        expensesBetween = { from, until -> expenses.filter { it.timestamp in from until until } },
        now = { now },
        zone = zone,
    )

    @Test
    fun periodBoundariesFollowCalendarAndMondayWeeks() {
        fun range(p: Period) = p.range(now, zone).let { Instant.ofEpochMilli(it.first).atZone(zone).toLocalDateTime() to Instant.ofEpochMilli(it.last + 1).atZone(zone).toLocalDateTime() }
        assertEquals(LocalDateTime.of(2026, 10, 12, 0, 0) to LocalDateTime.of(2026, 10, 18, 0, 0), range(Period.THIS_WEEK))
        assertEquals(LocalDateTime.of(2026, 10, 5, 0, 0) to LocalDateTime.of(2026, 10, 12, 0, 0), range(Period.LAST_WEEK))
        assertEquals(LocalDateTime.of(2026, 9, 1, 0, 0) to LocalDateTime.of(2026, 10, 1, 0, 0), range(Period.LAST_MONTH))
        assertEquals(LocalDateTime.of(2026, 10, 11, 0, 0) to LocalDateTime.of(2026, 10, 18, 0, 0), range(Period.LAST_7_DAYS))
    }

    @Test
    fun totalsFilterByCategoryAndPeriod() = runTest {
        val answer = executor.run(SpendQuery(categories = setOf(Category.Food), period = Period.LAST_MONTH, compareTo = Period.THIS_MONTH))
        assertEquals(500.0, answer.primary.total, 0.001)
        assertEquals(2, answer.primary.count)
        assertEquals(500.0, answer.comparison!!.total, 0.001)
    }

    @Test
    fun weekBoundaryIsExact() = runTest {
        assertEquals(500.0, executor.run(SpendQuery(period = Period.THIS_WEEK)).primary.total, 0.001)
        assertEquals(150.0, executor.run(SpendQuery(period = Period.LAST_WEEK)).primary.total, 0.001)
    }

    @Test
    fun merchantKeywordsNarrowToDelivery() = runTest {
        val answer = executor.run(SpendQuery(categories = setOf(Category.Food), merchantContains = listOf("swiggy", "zomato"), period = Period.THIS_YEAR))
        assertEquals(900.0, answer.primary.total, 0.001)
        assertEquals(listOf("Swiggy" to 700.0, "Zomato" to 200.0), answer.primary.topMerchants)
    }

    @Test
    fun yesterdayIsTheWholePreviousDay() = runTest {
        assertEquals(300.0, executor.run(SpendQuery(period = Period.YESTERDAY)).primary.total, 0.001)
    }

    @Test
    fun emptyPeriodIsZeroWithoutComparison() = runTest {
        val answer = executor.run(SpendQuery(period = Period.TODAY))
        assertEquals(0.0, answer.primary.total, 0.001)
        assertEquals(0.0, answer.primary.average, 0.001)
        assertNull(answer.comparison)
    }

    @Test
    fun describeShowsWhatWasAnswered() {
        assertEquals(
            "Food · Swiggy, Zomato · Last month vs This month",
            SpendQuery(categories = setOf(Category.Food), merchantContains = listOf("swiggy", "zomato"), period = Period.LAST_MONTH, compareTo = Period.THIS_MONTH).describe(),
        )
        assertEquals("This month", SpendQuery().describe())
    }
}

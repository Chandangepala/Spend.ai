package com.basic.spendai.insights

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class InsightsEngineTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    // Saturday 17 October 2026, 10:00 IST: "this week" is Sun 11 – Sat 17 Oct.
    private val now = LocalDateTime.of(2026, 10, 17, 10, 0).atZone(zone).toInstant()
    private val engine = InsightsEngine(zone)
    private var nextId = 1L

    private fun e(title: String, amount: Double, category: Category, date: LocalDate, hour: Int = 12) =
        Expense(nextId++, title, amount, category, date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli())

    private fun day(month: Int, d: Int) = LocalDate.of(2026, month, d)

    private inline fun <reified T : Finding> List<Finding>.only(): List<T> = filterIsInstance<T>()

    @Test
    fun weekBoundsAndTotals() {
        val report = engine.analyze(listOf(e("Tea", 20.0, Category.Food, day(10, 11), 0), e("Old", 999.0, Category.Food, day(10, 10), 23)), now)
        assertEquals(20.0, report.total, 0.001)
        assertEquals(1, report.count)
        assertEquals(Category.Food, report.topCategory)
    }

    @Test
    fun weekendSpikeOverFourWeeks() {
        // Sep 20 – Oct 17: four Saturdays at ₹1,000; ten weekday coffees at ₹100.
        val weekends = listOf(day(9, 26), day(10, 3), day(10, 10), day(10, 17)).map { e("Mall", 1000.0, Category.Shopping, it, 9) }
        val weekdays = (1..10).map { e("Coffee", 100.0, Category.Food, day(9, 21).plusDays(it.toLong() * 2 - 2).let { d -> if (d.dayOfWeek.value >= 6) d.plusDays(2) else d }) }
        val spike = engine.analyze(weekends + weekdays, now).findings.only<Finding.WeekendSpike>().single()
        assertEquals(500.0, spike.weekendPerDay, 0.001)
        assertEquals(50.0, spike.weekdayPerDay, 0.001)
        assertEquals(10.0, spike.ratio, 0.001)
    }

    @Test
    fun evenSpendingIsNotASpike() {
        val days = (0L until 28L).map { e("Lunch", 200.0, Category.Food, day(10, 17).minusDays(it), 9) }
        assertTrue(engine.analyze(days, now).findings.only<Finding.WeekendSpike>().isEmpty())
    }

    @Test
    fun monthlyChargesAreSubscriptions() {
        val expenses = listOf(
            e("Netflix", 649.0, Category.Entertainment, day(8, 18)),
            e("Netflix", 649.0, Category.Entertainment, day(9, 17)),
            e("Netflix", 649.0, Category.Entertainment, day(10, 16)),
            e("Cult.fit", 1500.0, Category.Health, day(9, 12)),
            e("Cult.fit", 1550.0, Category.Health, day(10, 12)), // within 10%
            e("Swiggy", 300.0, Category.Food, day(9, 20)),
            e("Swiggy", 900.0, Category.Food, day(10, 20 - 4)), // a month apart but amounts differ a lot
            e("Spotify", 119.0, Category.Entertainment, day(10, 1)), // only once
        )
        val subs = engine.analyze(expenses, now).findings.only<Finding.Subscriptions>().single()
        assertEquals(listOf(Subscription("Cult.fit", 1550.0), Subscription("Netflix", 649.0)), subs.items)
        assertEquals(2199.0, subs.monthlyTotal, 0.001)
    }

    @Test
    fun cancelledSubscriptionIsNotReported() {
        val expenses = listOf(e("Hotstar", 299.0, Category.Entertainment, day(7, 1)), e("Hotstar", 299.0, Category.Entertainment, day(7, 31)))
        assertTrue(engine.analyze(expenses, now).findings.only<Finding.Subscriptions>().isEmpty())
    }

    @Test
    fun largeSpendIsOverThreeTimesTheCategoryMedian() {
        val history = listOf(180.0, 200.0, 220.0, 250.0).mapIndexed { i, a -> e("Dinner $i", a, Category.Food, day(9, 1 + i * 5)) }
        val week = listOf(e("Anniversary dinner", 2400.0, Category.Food, day(10, 15)), e("Lunch", 210.0, Category.Food, day(10, 14)))
        val large = engine.analyze(history + week, now).findings.only<Finding.LargeSpend>().single()
        assertEquals("Anniversary dinner", large.title)
        assertEquals(210.0, large.typical, 0.001)
    }

    @Test
    fun categoryAboveUsualNeedsThreeWeeksOfHistory() {
        // 12 weeks of ₹100/week on transport, then ₹400 this week.
        val history = (1..12).map { w -> e("Metro", 100.0, Category.Transport, day(10, 10).minusWeeks(w.toLong() - 1)) }
        val week = listOf(e("Cab", 400.0, Category.Transport, day(10, 15)))
        val finding = engine.analyze(history + week, now).findings.only<Finding.CategoryVsUsual>().single()
        assertEquals(Category.Transport, finding.category)
        assertEquals(100.0, finding.usualWeek, 0.001)
        assertEquals(300, finding.percent)

        val tooNew = listOf(e("Metro", 100.0, Category.Transport, day(10, 3))) + week
        assertTrue(engine.analyze(tooNew, now).findings.only<Finding.CategoryVsUsual>().isEmpty())
    }

    @Test
    fun quietWeekHasNoFindings() {
        val report = engine.analyze(listOf(e("Tea", 20.0, Category.Food, day(10, 16))), now)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun reportSurvivesJsonStorage() {
        val history = listOf(180.0, 200.0, 220.0).mapIndexed { i, a -> e("Dinner $i", a, Category.Food, day(9, 1 + i * 5)) }
        val report = engine.analyze(history + e("Big dinner", 2400.0, Category.Food, day(10, 15)), now)
        assertEquals(report, Json.decodeFromString<WeeklyReport>(Json.encodeToString(report)))
    }
}

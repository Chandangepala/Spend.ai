package com.basic.spendai.reminders

import com.basic.spendai.budgets.BudgetProgress
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyReminderTest {
    private val inr = AppCurrency.INR
    private val zone = ZoneId.of("Asia/Kolkata")

    // --- What to say, and whether to say anything ---

    @Test
    fun quietOnDaysTheUserIsUpToDate() {
        assertNull(DailyReminder.message(loggedToday = 2, pendingCount = 0, pendingTotal = 0.0, budgets = emptyList(), currency = inr))
    }

    @Test
    fun nothingLoggedAsksToAdd() {
        val m = DailyReminder.message(0, 0, 0.0, emptyList(), inr)!!
        assertEquals(ReminderTarget.ADD, m.target)
        assertEquals("Add today's spending?", m.title)
    }

    @Test
    fun smsSpendsWaitingComeFirstEvenAfterLogging() {
        val m = DailyReminder.message(loggedToday = 3, pendingCount = 2, pendingTotal = 1240.0, budgets = emptyList(), currency = inr)!!
        assertEquals(ReminderTarget.REVIEW, m.target)
        assertEquals("2 spends to review", m.title)
        assertTrue(m.text, m.text.startsWith("₹1,240.00 from your bank SMS"))
        assertEquals("1 spend to review", DailyReminder.message(0, 1, 99.0, emptyList(), inr)!!.title)
    }

    @Test
    fun theTightestBudgetIsMentioned() {
        val budgets = listOf(
            BudgetProgress(Category.Shopping, limit = 5000.0, spent = 1000.0, daysLeft = 9),
            BudgetProgress(Category.Food, limit = 6000.0, spent = 5100.0, daysLeft = 9),
            BudgetProgress(Category.Transport, limit = 2000.0, spent = 1700.0, daysLeft = 9),
        )
        val m = DailyReminder.message(0, 0, 0.0, budgets, inr)!!
        assertTrue(m.text, m.text.endsWith("You're at 85% of your ${Category.Food.label} budget with 9 days left."))
        // Budgets alone never cause a reminder on an up-to-date day.
        assertNull(DailyReminder.message(1, 0, 0.0, budgets, inr))
    }

    // --- Suggested time ---

    private val now = LocalDateTime.of(2026, 10, 18, 12, 0).atZone(zone).toInstant()

    /** One expense per day at [times] ("HH:mm"), on consecutive days before [now]. */
    private fun days(vararg times: String) = times.mapIndexed { i, t ->
        LocalDate.of(2026, 10, 17).minusDays(i.toLong()).atTime(java.time.LocalTime.parse(t)).atZone(zone).toInstant().toEpochMilli()
    }

    @Test
    fun suggestionFollowsWhenSpendingUsuallyEnds() {
        // Median last spend 20:10, + 30 min = 20:40, rounded up to 20:45.
        assertEquals(20 * 60 + 45, DailyReminder.suggestMinutes(days("19:00", "20:10", "21:05", "20:00", "20:30"), now, zone))
        // Only each day's last spend counts.
        val withLunches = days("19:00", "20:10", "21:05", "20:00", "20:30") + days("13:00", "13:00", "12:30")
        assertEquals(20 * 60 + 45, DailyReminder.suggestMinutes(withLunches, now, zone))
    }

    @Test
    fun suggestionStaysInTheEvening() {
        assertEquals(18 * 60, DailyReminder.suggestMinutes(days("09:00", "10:00", "11:00", "12:00", "13:00"), now, zone))
        assertEquals(22 * 60 + 30, DailyReminder.suggestMinutes(days("23:00", "23:30", "23:10", "23:50", "23:20"), now, zone))
    }

    @Test
    fun noSuggestionWithoutEnoughHistory() {
        assertNull(DailyReminder.suggestMinutes(days("20:00", "20:00", "20:00", "20:00"), now, zone))
        // Bare dates (midnight) and expenses older than 30 days don't count.
        val old = LocalDate.of(2026, 8, 1).atTime(20, 0).atZone(zone).toInstant().toEpochMilli()
        assertNull(DailyReminder.suggestMinutes(days("20:00", "20:00", "20:00", "00:00", "00:00") + old, now, zone))
    }

    // --- When it fires ---

    @Test
    fun nextTriggerIsLaterTodayOrTomorrow() {
        val evening = ZonedDateTime.of(2026, 10, 18, 18, 0, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 18, 21, 30, 0, 0, zone), DailyReminder.nextTrigger(21 * 60 + 30, evening))
        val late = ZonedDateTime.of(2026, 10, 18, 21, 30, 0, 0, zone)
        assertEquals(ZonedDateTime.of(2026, 10, 19, 21, 30, 0, 0, zone), DailyReminder.nextTrigger(21 * 60 + 30, late))
    }

    @Test
    fun nextTriggerKeepsTheClockTimeAcrossDaylightSaving() {
        val london = ZoneId.of("Europe/London")
        // Clocks go back on 25 Oct 2026: the reminder is still at 9 PM local time.
        val before = ZonedDateTime.of(2026, 10, 24, 22, 0, 0, 0, london)
        assertEquals(ZonedDateTime.of(2026, 10, 25, 21, 0, 0, 0, london), DailyReminder.nextTrigger(21 * 60, before))
    }
}

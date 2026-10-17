package com.basic.spendai.ai

import com.basic.spendai.budgets.GoalMath
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CoachWriterTest {

    private val today = LocalDate.of(2026, 10, 21)
    private val suggestion = CoachWriter.Input(
        CoachWriter.Topic.Suggestion(Category.Food, 6100.0, listOf(5800.0, 6400.0, 6100.0)), AppCurrency.INR,
    )
    private val whatIf = CoachWriter.Input(CoachWriter.Topic.WhatIf(Category.Food, 30, 6100.0, 1830.0, 21960.0), AppCurrency.INR)
    private val deadline = LocalDate.of(2027, 3, 31)
    private val goal = CoachWriter.Input(
        CoachWriter.Topic.Goal("Goa trip", GoalMath.plan(12_000.0, 30_000.0, deadline, today), deadline), AppCurrency.INR,
    )

    @Test
    fun templates() {
        assertEquals(
            "Dining & Drinks has been about ₹6,100.00 in a typical month, so ₹6,100.00 is a realistic budget.",
            CoachWriter.template(suggestion),
        )
        assertEquals(
            "Cutting Dining & Drinks by 30% would save about ₹1,830.00 a month — ₹21,960.00 a year.",
            CoachWriter.template(whatIf),
        )
        assertEquals("Put aside ₹3,000.00 a month to reach Goa trip by Mar 31, 2027.", CoachWriter.template(goal))
        val noDate = goal.copy(topic = CoachWriter.Topic.Goal("Laptop", GoalMath.plan(5000.0, 60_000.0, null, today), null))
        assertEquals("₹55,000.00 to go for Laptop.", CoachWriter.template(noDate))
    }

    @Test
    fun validReplyIsUsed() = runTest {
        val reply = "Nice! Setting aside ₹3,000 a month for 6 months gets you to Goa trip by Mar 31, 2027."
        val result = CoachWriter.run(FakeAiProvider(reply), goal)
        assertTrue(result.fromAi)
        assertEquals(reply, result.value)
    }

    @Test
    fun inventedNumbersAdviceAndFailuresFallBack() = runTest {
        // 2,000 isn't a computed number.
        assertFalse(CoachWriter.run(FakeAiProvider("Try ₹2,000 a month and you'll hit Goa trip early."), goal).fromAi)
        assertFalse(CoachWriter.run(FakeAiProvider("Put the ₹1,830.00 you save into mutual funds each month."), whatIf).fromAi)
        val failed = CoachWriter.run(FakeAiProvider(null), suggestion)
        assertFalse(failed.fromAi)
        assertEquals(CoachWriter.template(suggestion), failed.value)
    }

    @Test
    fun promptCarriesOnlyFacts() = runTest {
        val fake = FakeAiProvider(null)
        CoachWriter.run(fake, whatIf)
        assertTrue(fake.prompts.single().contains("Cutting it by 30% saves ₹1,830.00 a month, ₹21,960.00 a year."))
    }
}

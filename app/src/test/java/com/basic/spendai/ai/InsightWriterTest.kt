package com.basic.spendai.ai

import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.insights.Finding
import com.basic.spendai.insights.Subscription
import com.basic.spendai.insights.WeeklyReport
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class InsightWriterTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun ms(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()

    private val report = WeeklyReport(
        from = ms(LocalDate.of(2026, 10, 11)),
        until = ms(LocalDate.of(2026, 10, 18)),
        total = 6150.0,
        count = 9,
        topCategory = Category.Food,
        findings = listOf(
            Finding.CategoryVsUsual(Category.Food, 4200.0, 3000.0, 40),
            Finding.WeekendSpike(500.0, 250.0, 2.0),
            Finding.Subscriptions(listOf(Subscription("Netflix", 649.0)), 649.0),
        ),
    )
    private val input = InsightWriter.Input(report, AppCurrency.INR, zone)

    @Test
    fun factsCarryOnlyComputedNumbers() {
        val facts = InsightWriter.facts(input)
        assertEquals("Week Oct 11 – Oct 17: spent ₹6,150.00 across 9 expenses.", facts[0])
        assertTrue(facts[1], facts[1] == "Dining & Drinks: ₹4,200.00 this week vs a usual ₹3,000.00 (40% more).")
        assertTrue(facts[2], facts[2] == "Weekends: ₹500.00 per day vs ₹250.00 per weekday (2 times as much).")
        assertTrue(facts[3], facts[3] == "Repeating monthly payments: Netflix ₹649.00 (₹649.00 a month).")
    }

    @Test
    fun templateHasTotalTwoFindingsAndATip() {
        val text = InsightWriter.template(input)
        assertTrue(text, text.startsWith("You spent ₹6,150.00 across 9 expenses. "))
        assertTrue(text, text.contains("Dining & Drinks: ₹4,200.00"))
        assertTrue(text, text.contains("Weekends:"))
        assertFalse(text, text.contains("Netflix")) // only two findings make it into the sentence
        assertTrue(text, text.endsWith("Tip: plan one low-spend day next weekend."))
    }

    @Test
    fun quietAndEmptyWeeks() {
        val quiet = input.copy(report = report.copy(findings = emptyList()))
        assertEquals("You spent ₹6,150.00 across 9 expenses. A steady week with nothing unusual.", InsightWriter.template(quiet))
        assertEquals("Oct 11 – Oct 17", InsightWriter.range(quiet))
        val empty = input.copy(report = report.copy(total = 0.0, count = 0, findings = emptyList()))
        assertEquals("No spending recorded this week.", InsightWriter.template(empty))
    }

    @Test
    fun modelSummaryWithGivenNumbersIsUsed() = runTest {
        val reply = "A busier week: ₹6,150 in total, with dining at ₹4,200 versus your usual ₹3,000. Weekends cost about 2 times as much per day. Tip: try a home-cooked Saturday."
        val result = InsightWriter.run(FakeAiProvider(reply), input)
        assertTrue(result.fromAi)
        assertEquals(reply, result.value)
    }

    @Test
    fun inventedNumbersOrAdviceAreRejected() = runTest {
        val invented = InsightWriter.run(FakeAiProvider("You could save ₹1,200 a month by skipping weekend dinners out."), input)
        assertFalse(invented.fromAi)
        val advice = InsightWriter.run(FakeAiProvider("Dining was ₹4,200. Consider moving the extra into mutual funds instead."), input)
        assertFalse(advice.fromAi)
    }

    @Test
    fun paymentNamesInTheFactsDontCountAsAdvice() = runTest {
        val withLoan = input.copy(report = report.copy(findings = listOf(Finding.LargeSpend("HDFC Loan EMI", 9000.0, Category.Finance, 2500.0))))
        val reply = "This week's standout was the HDFC Loan EMI at ₹9,000, well above a usual ₹2,500 payment."
        assertTrue(InsightWriter.run(FakeAiProvider(reply), withLoan).fromAi)
    }
}

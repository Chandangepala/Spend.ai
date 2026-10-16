package com.basic.spendai.ai

import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.stats.Metric
import com.basic.spendai.stats.Period
import com.basic.spendai.stats.PeriodStats
import com.basic.spendai.stats.SpendAnswer
import com.basic.spendai.stats.SpendQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AskTest {

    private val translator = SpendQueryTranslator(LocalDate.of(2026, 10, 17))

    // --- Question -> query, rules ---

    @Test
    fun rulesReadPeriodsInOrderAndCategories() {
        assertEquals(
            SpendQuery(Metric.TOTAL, setOf(Category.Food), listOf("swiggy", "zomato"), Period.LAST_MONTH, Period.THIS_MONTH),
            SpendQueryTranslator.Rules.translate("How much did I spend on food delivery last month vs this month?"),
        )
    }

    @Test
    fun rulesPickTheKindOfAnswer() {
        assertEquals(Metric.COUNT, SpendQueryTranslator.Rules.translate("How many times did I take an auto last week?").metric)
        assertEquals(Period.LAST_WEEK, SpendQueryTranslator.Rules.translate("How many times did I take an auto last week?").period)
        assertEquals(Metric.TOP_MERCHANTS, SpendQueryTranslator.Rules.translate("Where did I spend the most this month?").metric)
        assertEquals(Metric.AVERAGE, SpendQueryTranslator.Rules.translate("average bill in the last 30 days").metric)
        assertEquals(Period.LAST_30_DAYS, SpendQueryTranslator.Rules.translate("average bill in the last 30 days").period)
    }

    @Test
    fun rulesDefaultToTotalThisMonth() {
        assertEquals(SpendQuery(), SpendQueryTranslator.Rules.translate("how am I doing?"))
    }

    // --- Question -> query, AI ---

    @Test
    fun validModelQueryIsUsed() = runTest {
        val provider = FakeAiProvider(
            """{"metric":"total","categories":["Food"],"merchant_contains":["Swiggy","Zomato"],"period":"last_month","compare_to":"this_month"}"""
        )
        val result = translator.run(provider, "food delivery last month vs this month")
        assertTrue(result.fromAi)
        assertEquals(SpendQuery(Metric.TOTAL, setOf(Category.Food), listOf("swiggy", "zomato"), Period.LAST_MONTH, Period.THIS_MONTH), result.value)
        assertTrue(provider.prompts.single().contains("Today is Saturday, 17 October 2026"))
    }

    @Test
    fun unknownPeriodOrCategoryFallsBackToRules() = runTest {
        val badPeriod = FakeAiProvider("""{"metric":"total","categories":[],"period":"last_quarter","compare_to":null}""")
        assertFalse(translator.run(badPeriod, "spend last month").fromAi)
        val badCategory = FakeAiProvider("""{"metric":"total","categories":["Groceries"],"period":"this_month"}""")
        val result = translator.run(badCategory, "groceries this month")
        assertFalse(result.fromAi)
        assertEquals(setOf(Category.Food), result.value.categories)
    }

    // --- Numbers -> sentence ---

    private val answer = SpendAnswer(
        query = SpendQuery(Metric.TOTAL, setOf(Category.Food), emptyList(), Period.LAST_MONTH, Period.THIS_MONTH),
        primary = PeriodStats(Period.LAST_MONTH, 4200.0, 12, emptyList()),
        comparison = PeriodStats(Period.THIS_MONTH, 3100.0, 9, emptyList()),
    )
    private val input = AnswerWriter.Input("food last month vs this month", answer, AppCurrency.INR)

    @Test
    fun templateSaysWhichPeriodWasMore() {
        val text = AnswerWriter.template(input)
        assertTrue(text, text.startsWith("You spent ₹4,200.00 on Food last month, vs ₹3,100.00 this month"))
        assertTrue(text, text.contains("(last month was 35% more)"))
    }

    @Test
    fun zeroSideSaysNothingInsteadOfAHundredPercent() {
        val empty = answer.copy(primary = PeriodStats(Period.LAST_MONTH, 0.0, 0, emptyList()))
        val text = AnswerWriter.template(input.copy(answer = empty))
        assertEquals("You spent nothing on Food last month, vs ₹3,100.00 this month.", text)
        assertFalse(AnswerWriter.facts(input.copy(answer = empty)).contains("Change"))
    }

    @Test
    fun modelSentenceWithOnlyGivenNumbersIsUsed() = runTest {
        val reply = "You spent ₹4,200 on food last month and ₹3,100 so far this month, about 35% more last month."
        val result = AnswerWriter.run(FakeAiProvider(reply), input)
        assertTrue(result.fromAi)
        assertEquals(reply, result.value)
    }

    @Test
    fun modelSentenceWithAnInventedNumberIsRejected() = runTest {
        // 7,300 is a total the model added up itself; it isn't in the facts.
        val result = AnswerWriter.run(FakeAiProvider("Together that's ₹7,300 on food across both months."), input)
        assertFalse(result.fromAi)
        assertEquals(AnswerWriter.template(input), result.value)
    }

    @Test
    fun factsGiveTheModelOnlyComputedNumbers() {
        val facts = AnswerWriter.facts(input)
        assertTrue(facts, facts.contains("Last month (Food): total ₹4,200.00, 12 expenses, average ₹350.00"))
        assertTrue(facts, facts.contains("Change: Last month is 35% more than This month"))
    }
}

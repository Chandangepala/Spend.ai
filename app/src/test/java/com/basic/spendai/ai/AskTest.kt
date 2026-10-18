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

class AskTest {

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

    // --- Shop names: leftover words that appear in the user's own expense titles ---

    private val titles = listOf("Swiggy", "Zomato order", "Uber", "Ola", "Rapido", "Paytm*blinkit4412", "Starbucks", "Pizza Hut", "Amazon", "Netflix")

    private fun read(question: String) = SpendQueryTranslator.translate(question, titles)

    @Test
    fun shopsTheUserBuysFromBecomeFilters() {
        assertEquals(SpendQuery(Metric.COUNT, emptySet(), listOf("swiggy"), Period.LAST_WEEK), read("How many times did I order from Swiggy last week?"))
        assertEquals(listOf("uber"), read("How much on Uber in the past 7 days").merchantContains)
        assertEquals(listOf("starbucks"), read("how much at starbucks this month").merchantContains)
        assertEquals(listOf("zomato"), read("zomato orders this year").merchantContains)
        // Rides are Transport, Rapido is the shop: both apply.
        assertEquals(SpendQuery(Metric.COUNT, setOf(Category.Transport), listOf("rapido"), Period.LAST_WEEK), read("how many rapido rides last week"))
        // A title like "Paytm*blinkit4412" still counts as Blinkit.
        assertEquals(SpendQuery(Metric.COUNT, emptySet(), listOf("blinkit"), Period.THIS_WEEK), read("how many blinkit orders this week"))
        assertEquals(listOf("uber", "ola"), read("uber vs ola last month").merchantContains)
        assertEquals(listOf("pizza"), read("how much on pizza this week").merchantContains)
    }

    @Test
    fun wordsNotInAnyTitleAreIgnored() {
        assertEquals(SpendQuery(Metric.TOTAL, emptySet(), emptyList(), Period.THIS_MONTH), read("show my spending trend this month"))
        assertEquals(emptyList<String>(), read("what did I spend on my sister's wedding this year").merchantContains)
        // Never bought at Ikea: the whole month is answered, and the card's scope line shows no shop.
        assertEquals(emptyList<String>(), read("how much did I spend at ikea last month").merchantContains)
        // Category words stay categories even if a title contains them.
        assertEquals(SpendQuery(Metric.AVERAGE, setOf(Category.Shopping), emptyList(), Period.THIS_YEAR), read("average amazon purchase this year"))
    }

    @Test
    fun theOtherQuestionsReadAsBefore() {
        assertEquals(SpendQuery(Metric.TOTAL, setOf(Category.Food, Category.Groceries), emptyList(), Period.LAST_MONTH, Period.THIS_MONTH), read("food last month vs this month"))
        assertEquals(SpendQuery(Metric.TOP_MERCHANTS, emptySet(), emptyList(), Period.THIS_MONTH), read("Where did I spend the most this month?"))
        assertEquals(SpendQuery(Metric.TOTAL, setOf(Category.Shopping), emptyList(), Period.THIS_WEEK, Period.LAST_WEEK), read("Did I spend more on shopping this week than last week?"))
        assertEquals(SpendQuery(Metric.TOTAL, setOf(Category.Bills), emptyList(), Period.LAST_MONTH), read("electricity bill last month"))
        assertEquals(listOf("swiggy"), SpendQueryTranslator.Rules.unknownWords("How many times did I order from Swiggy last week?"))
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
        assertTrue(text, text.startsWith("You spent ₹4,200.00 on Dining & Drinks last month, vs ₹3,100.00 this month"))
        assertTrue(text, text.contains("(last month was 35% more)"))
    }

    @Test
    fun zeroSideSaysNothingInsteadOfAHundredPercent() {
        val empty = answer.copy(primary = PeriodStats(Period.LAST_MONTH, 0.0, 0, emptyList()))
        val text = AnswerWriter.template(input.copy(answer = empty))
        assertEquals("You spent nothing on Dining & Drinks last month, vs ₹3,100.00 this month.", text)
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
        assertEquals(
            "Last month (Dining & Drinks): total ₹4,200.00\nThis month (Dining & Drinks): total ₹3,100.00\nChange: Last month is 35% more than This month",
            facts,
        )
        // Only the numbers this kind of question needs: no average to mistake for the total.
        val avg = input.copy(answer = answer.copy(query = answer.query.copy(metric = Metric.AVERAGE), comparison = null))
        assertEquals("Last month (Dining & Drinks): average ₹350.00 per expense, 12 expenses", AnswerWriter.facts(avg))
    }

    // --- Sentence checks found on a Pixel 6a ---

    private fun sentence(reply: String, answer: SpendAnswer, question: String = "q") =
        kotlinx.coroutines.runBlocking { AnswerWriter.run(FakeAiProvider(reply), AnswerWriter.Input(question, answer, AppCurrency.INR)) }

    @Test
    fun sentenceAboutTheWrongPeriodIsRejected() {
        val avg = SpendAnswer(SpendQuery(Metric.AVERAGE, setOf(Category.Groceries), period = Period.LAST_30_DAYS), PeriodStats(Period.LAST_30_DAYS, 3960.0, 6, emptyList()), null)
        // What Gemma 1B wrote: a real number, but a week instead of 30 days and a bill instead of an average.
        assertFalse(sentence("Last week's grocery bill was ₹660.00.", avg).fromAi)
        assertTrue(sentence("Your average grocery expense over the last 30 days was ₹660.00.", avg).fromAi)
        // Comparisons may name both of their periods.
        assertTrue(sentence("Last month's dining total was ₹4200, while this month's was ₹3100.", answer).fromAi)
    }

    @Test
    fun numberWordsAreCheckedLikeDigits() {
        val count = SpendAnswer(SpendQuery(Metric.COUNT, merchantContains = listOf("swiggy"), period = Period.LAST_WEEK), PeriodStats(Period.LAST_WEEK, 1240.0, 4, emptyList()), null)
        assertTrue(sentence("Last week you ordered from Swiggy four times.", count).fromAi)
        assertFalse(sentence("Last week you ordered from Swiggy five times.", count).fromAi)
        assertFalse(sentence("Last week you spent over a thousand rupees on Swiggy.", count).fromAi)
        assertEquals("ordered 4 times, 2 of them big", AnswerWriter.digitsForWords("ordered four times, twice of them big"))
    }
}

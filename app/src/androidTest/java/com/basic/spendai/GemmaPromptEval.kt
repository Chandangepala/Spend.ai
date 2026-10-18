package com.basic.spendai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.basic.spendai.ai.AiProviders
import com.basic.spendai.ai.GemmaModel
import com.basic.spendai.ai.AnswerWriter
import com.basic.spendai.ai.QuickEntryParser
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.stats.Metric
import com.basic.spendai.stats.Period
import com.basic.spendai.stats.PeriodStats
import com.basic.spendai.stats.SpendAnswer
import com.basic.spendai.stats.SpendQuery
import com.basic.spendai.data.Category
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Scores the quick-entry prompt against the real Gemma model on this phone: for each case,
 * the raw reply, whether validation accepts it, and whether it's right. Logged as "GemmaEval".
 * Opt-in (it takes a minute or two): `-e gemmaEval true`.
 */
@RunWith(AndroidJUnit4::class)
class GemmaPromptEval {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun optIn() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("gemmaEval") == "true")
        assumeTrue(GemmaModel.isInstalled(context))
    }

    private val quickCases = listOf(
        "200 chai and samosa with team, 450 auto" to listOf(200.0 to Category.Food, 450.0 to Category.Transport),
        "lunch 350 and coffee 120, uber 230" to listOf(350.0 to Category.Food, 120.0 to Category.Food, 230.0 to Category.Transport),
        "paid 1200 for groceries at dmart" to listOf(1200.0 to Category.Groceries),
        "movie tickets 600, popcorn 250" to listOf(600.0 to Category.Entertainment, 250.0 to Category.Food),
        "rent 15000" to listOf(15000.0 to Category.Home),
        "petrol 500 and parking 40" to listOf(500.0 to Category.Transport, 40.0 to Category.Transport),
        "₹99 netflix, ₹199 spotify" to listOf(99.0 to Category.Entertainment, 199.0 to Category.Entertainment),
        "gave 2000 to mom for her birthday gift" to listOf(2000.0 to Category.Gifts),
        "dinner at barbeque nation 2,450" to listOf(2450.0 to Category.Food),
        "medicines 340, doctor fee 500, cab 180" to listOf(340.0 to Category.Health, 500.0 to Category.Health, 180.0 to Category.Transport),
        "blinkit 540" to listOf(540.0 to Category.Groceries),
        "pani puri 60, metro card 200" to listOf(60.0 to Category.Food, 200.0 to Category.Transport),
        "carpenter 1500" to listOf(1500.0 to Category.Home),
    )

    @Test
    fun quickEntry(): Unit = runBlocking {
        val provider = AiProviders.gemma(context)
        var accepted = 0
        var modelRight = 0
        var rulesRight = 0
        var finalRight = 0
        for ((note, expected) in quickCases) {
            fun matches(items: List<com.basic.spendai.ai.QuickEntryItem>?) = items != null && items.map { it.amount to it.category } == expected
            val start = System.nanoTime()
            val asks = QuickEntryParser.asksModel(note)
            val raw = if (asks) provider.generate(QuickEntryParser.promptFor(note), QuickEntryParser.replyTokens) else null
            val ms = (System.nanoTime() - start) / 1_000_000
            val items = raw?.let { QuickEntryParser.judge(it, note) }
            val rules = QuickEntryParser.Rules.parse(note)
            val final = QuickEntryParser.run(provider, note)
            if (items != null) accepted++
            if (matches(items)) modelRight++
            if (matches(rules)) rulesRight++
            if (matches(final.value)) finalRight++
            Log.i(
                TAG,
                "QUICK model=${if (!asks) "SKIPPED" else verdict(items != null, matches(items))} rules=${ok(matches(rules))} final=${ok(matches(final.value))}${if (final.fromAi) "(AI)" else ""} " +
                    "(${ms}ms) \"$note\"\n  raw=${raw?.replace("\n", "⏎")}\n  final=${final.value.map { "${it.title}/${it.amount}/${it.category}" }}",
            )
        }
        Log.i(TAG, "QUICK SCORE model accepted=$accepted right=$modelRight | rules right=$rulesRight | final right=$finalRight of ${quickCases.size}")
    }

    /** Written after the prompts were tuned, to check they weren't fitted to the cases above. */
    @Test
    fun heldOut(): Unit = runBlocking {
        val provider = AiProviders.gemma(context)
        val quick = listOf(
            "samosa 40" to listOf(40.0 to Category.Food),
            "ola 230" to listOf(230.0 to Category.Transport),
            "dentist 1200" to listOf(1200.0 to Category.Health),
            "school fees 25000" to listOf(25000.0 to Category.Education),
            "birthday cake 900" to listOf(900.0 to Category.Food),
            "wifi recharge 799" to listOf(799.0 to Category.Bills),
            "zara 2500" to listOf(2500.0 to Category.Shopping),
            "physio 800" to listOf(800.0 to Category.Health),
            "tolls 150, parking 60" to listOf(150.0 to Category.Transport, 60.0 to Category.Transport),
            "maid salary 4000" to listOf(4000.0 to Category.Home),
        )
        var quickRight = 0
        var quickRules = 0
        for ((note, expected) in quick) {
            val final = QuickEntryParser.run(provider, note)
            val r = final.value.map { it.amount to it.category } == expected
            if (r) quickRight++
            if (QuickEntryParser.Rules.parse(note).map { it.amount to it.category } == expected) quickRules++
            Log.i(TAG, "HELD QUICK final=${ok(r)}${if (final.fromAi) "(AI)" else ""} model=${QuickEntryParser.asksModel(note)} \"$note\" -> ${final.value.map { "${it.title}/${it.category}" }}")
        }
        Log.i(TAG, "HELD SCORE quick final=$quickRight rules=$quickRules of ${quick.size}")
    }

    @Test
    fun askSentence(): Unit = runBlocking {
        val provider = AiProviders.gemma(context)
        fun stats(p: Period, total: Double, count: Int, top: List<Pair<String, Double>> = emptyList()) = PeriodStats(p, total, count, top)
        val cases = listOf(
            "How much did I spend on transport this month?" to SpendAnswer(SpendQuery(Metric.TOTAL, setOf(Category.Transport)), stats(Period.THIS_MONTH, 1730.0, 9), null),
            "food last month vs this month" to SpendAnswer(
                SpendQuery(Metric.TOTAL, setOf(Category.Food), period = Period.LAST_MONTH, compareTo = Period.THIS_MONTH),
                stats(Period.LAST_MONTH, 4200.0, 12), stats(Period.THIS_MONTH, 3100.0, 9),
            ),
            "How many times did I order from Swiggy last week?" to SpendAnswer(SpendQuery(Metric.COUNT, merchantContains = listOf("swiggy"), period = Period.LAST_WEEK), stats(Period.LAST_WEEK, 1240.0, 4), null),
            "Where did I spend the most this month?" to SpendAnswer(
                SpendQuery(Metric.TOP_MERCHANTS), stats(Period.THIS_MONTH, 25778.78, 25, listOf("Policybazaar" to 2597.5, "Googleservis" to 1906.0, "Uber" to 640.0)), null,
            ),
            "average grocery bill in the last 30 days" to SpendAnswer(SpendQuery(Metric.AVERAGE, setOf(Category.Groceries), period = Period.LAST_30_DAYS), stats(Period.LAST_30_DAYS, 3960.0, 6), null),
        )
        var accepted = 0
        var rightCount = 0
        for ((question, answer) in cases) {
            val input = AnswerWriter.Input(question, answer, AppCurrency.INR)
            val start = System.nanoTime()
            val raw = provider.generate(AnswerWriter.promptFor(input), AnswerWriter.replyTokens)
            val ms = (System.nanoTime() - start) / 1_000_000
            val judged = raw?.let { AnswerWriter.judge(it, input) }
            if (judged != null) accepted++
            // The number that answers the question must be the one stated.
            val p = answer.primary
            val key = when (answer.query.metric) {
                Metric.TOTAL -> listOf(p.total)
                Metric.COUNT -> listOf(p.count.toDouble())
                Metric.AVERAGE -> listOf(p.average)
                Metric.TOP_MERCHANTS -> emptyList()
            }
            val numbers = judged?.let { com.basic.spendai.ai.NumberCheck.numbersIn(AnswerWriter.digitsForWords(it)) }.orEmpty()
            val right = judged != null && key.all { k -> numbers.any { kotlin.math.abs(it - k) < 0.01 } } &&
                (answer.query.metric != Metric.TOP_MERCHANTS || judged.contains(p.topMerchants.first().first))
            if (right) rightCount++
            Log.i(TAG, "SENTENCE ${if (judged == null) "REJECTED" else if (right) "RIGHT" else "WRONG"} (${ms}ms) \"$question\"\n  raw=${raw?.replace("\n", "⏎")}\n  template=${AnswerWriter.template(input)}")
        }
        Log.i(TAG, "SENTENCE SCORE accepted=$accepted right=$rightCount of ${cases.size}")
    }

    private fun ok(right: Boolean) = if (right) "RIGHT" else "WRONG"
    private fun verdict(accepted: Boolean, right: Boolean) = if (!accepted) "REJECTED" else ok(right)

    private companion object {
        const val TAG = "GemmaEval"
    }
}

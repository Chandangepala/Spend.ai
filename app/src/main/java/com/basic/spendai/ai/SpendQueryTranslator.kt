package com.basic.spendai.ai

import com.basic.spendai.data.Category
import com.basic.spendai.stats.Metric
import com.basic.spendai.stats.Period
import com.basic.spendai.stats.SpendQuery
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns "How much did I spend on food delivery last month vs this month?" into a [SpendQuery].
 * The model only picks from fixed options; the numbers are computed later by code.
 */
class SpendQueryTranslator(private val today: LocalDate = LocalDate.now()) : AiTask<String, SpendQuery>() {
    override val maxTokens = 128

    @Serializable
    private data class Reply(
        val metric: String = "",
        val categories: List<String> = emptyList(),
        @SerialName("merchant_contains") val merchantContains: List<String> = emptyList(),
        val period: String = "",
        @SerialName("compare_to") val compareTo: String? = null,
    )

    override fun prompt(input: String) = """
        |Turn this question about the user's spending into JSON only, no other text:
        |{"metric": "<total, count, average or top_merchants>", "categories": [<any of: $CATEGORY_NAMES>], "merchant_contains": ["<shop name word>"], "period": "<one of: $PERIODS>", "compare_to": "<a period or null>"}
        |Use [] when no category or shop is named. Use compare_to only when the question compares two periods.
        |Today is ${today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH))}.
        """.trimMargin() + "\nQuestion: " + input.take(MAX_QUESTION_CHARS)

    override fun parse(reply: String, input: String): SpendQuery? {
        val decoded = AiJson.decode<Reply>(reply) ?: return null
        val metric = Metric.entries.firstOrNull { it.name.equals(decoded.metric.trim(), ignoreCase = true) } ?: return null
        val period = periodOrNull(decoded.period) ?: return null
        val compareTo = decoded.compareTo?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
            ?.let { periodOrNull(it) ?: return null }
            ?.takeIf { it != period }
        // One unknown category means the model drifted from the list; don't half-trust it.
        val categories = decoded.categories.map { categoryOrNull(it) ?: return null }.toSet()
        val merchants = decoded.merchantContains.map { it.trim().lowercase() }
            .filter { it.length in 2..20 && it.all { c -> c.isLetterOrDigit() || c == ' ' } }
            .take(MAX_MERCHANTS)
        return SpendQuery(metric, categories, merchants, period, compareTo)
    }

    override fun fallback(input: String) = Rules.translate(input)

    private fun periodOrNull(name: String) =
        Period.entries.firstOrNull { it.name.equals(name.trim().replace(' ', '_'), ignoreCase = true) }

    companion object {
        private val PERIODS = Period.entries.joinToString(", ") { it.name.lowercase() }
        private const val MAX_QUESTION_CHARS = 300
        private const val MAX_MERCHANTS = 5
    }

    /** The no-AI path: keyword matching for periods, categories and the kind of answer wanted. */
    object Rules {
        private val o = setOf(RegexOption.IGNORE_CASE)

        // Longer phrases first so "last 30 days" isn't read as "last month"'s cousin.
        private val periodPhrases = listOf(
            Period.LAST_30_DAYS to Regex("""\b(last|past)\s+30\s+days\b|\bpast\s+month\b""", o),
            Period.LAST_7_DAYS to Regex("""\b(last|past)\s+(7|seven)\s+days\b|\bpast\s+week\b""", o),
            Period.TODAY to Regex("""\btoday\b""", o),
            Period.YESTERDAY to Regex("""\byesterday\b""", o),
            Period.THIS_WEEK to Regex("""\bthis\s+week\b""", o),
            Period.LAST_WEEK to Regex("""\b(last|previous)\s+week\b""", o),
            Period.THIS_MONTH to Regex("""\bthis\s+month\b""", o),
            Period.LAST_MONTH to Regex("""\b(last|previous)\s+month\b""", o),
            Period.THIS_YEAR to Regex("""\bthis\s+year\b""", o),
            Period.ALL_TIME to Regex("""\b(all\s+time|ever|overall|so\s+far)\b""", o),
        )
        private val categoryWords = mapOf(
            Category.Food to """food|eating|restaurants?|groceries|grocery|dining|meals?|snacks?""",
            Category.Transport to """transport|travel|cabs?|autos?|fuel|petrol|commute|rides?""",
            Category.Shopping to """shopping|clothes|amazon|flipkart""",
            Category.Bills to """bills?|rent|recharges?|utilities|electricity""",
            Category.Entertainment to """entertainment|movies?|subscriptions?|netflix""",
            Category.Health to """health|medical|medicines?|doctors?|pharmacy""",
        ).mapValues { (_, words) -> Regex("""\b($words)\b""", o) }
        private val delivery = Regex("""\bdelivery\b""", o)
        private val countWords = Regex("""\b(how\s+many|number\s+of|count|times)\b""", o)
        private val averageWords = Regex("""\b(average|avg|typical)\b""", o)
        private val topWords = Regex("""\b(top|most|biggest|where\s+did|which\s+(shop|shops|merchant|merchants|place|places))\b""", o)

        fun translate(question: String): SpendQuery {
            // Periods in the order they're mentioned: "last month vs this month".
            val periods = periodPhrases
                .flatMap { (period, regex) -> regex.findAll(question).map { it.range.first to period } }
                .sortedBy { it.first }
                .map { it.second }
                .distinct()
            val categories = categoryWords.filterValues { it.containsMatchIn(question) }.keys
            val merchants = if (Category.Food in categories && delivery.containsMatchIn(question)) listOf("swiggy", "zomato") else emptyList()
            val metric = when {
                countWords.containsMatchIn(question) -> Metric.COUNT
                averageWords.containsMatchIn(question) -> Metric.AVERAGE
                topWords.containsMatchIn(question) -> Metric.TOP_MERCHANTS
                else -> Metric.TOTAL
            }
            return SpendQuery(
                metric = metric,
                categories = categories,
                merchantContains = merchants,
                period = periods.firstOrNull() ?: Period.THIS_MONTH,
                compareTo = periods.getOrNull(1),
            )
        }
    }
}

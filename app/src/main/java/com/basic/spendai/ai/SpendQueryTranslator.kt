package com.basic.spendai.ai

import com.basic.spendai.data.Category
import com.basic.spendai.stats.Metric
import com.basic.spendai.stats.Period
import com.basic.spendai.stats.SpendQuery

/**
 * Turns "How many times did I order from Swiggy last week?" into a [SpendQuery], in code.
 * Periods, categories and the kind of answer come from keywords. Other words ("swiggy", "pizza")
 * become shop filters only if they appear in the user's own expense titles, so "trend" or
 * "wedding" are ignored. Tested on a phone, this beat asking Gemma 1B which words are brands: it
 * said yes to "trend" and no to "Blinkit". The model still writes the answer sentence.
 */
object SpendQueryTranslator {
    /** [titles]: the user's expense titles, the vocabulary of shops they actually buy from. */
    fun translate(question: String, titles: List<String>): SpendQuery = Rules.translate(question, Rules.titleWords(titles))

    /** The no-AI path: keyword matching for periods, categories and the kind of answer wanted. */
    object Rules {
        private const val MAX_MERCHANTS = 5
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
            Category.Food to """food|dining|eating\s+out|restaurants?|cafes?|drinks|meals?|snacks?|delivery""",
            Category.Groceries to """food|groceries|grocery|vegetables|veggies|supermarket""",
            Category.Transport to """transport|cabs?|autos?|fuel|petrol|commute|rides?""",
            Category.Travel to """travel|trips?|flights?|trains?|hotels?|stays?|vacations?""",
            Category.Shopping to """shopping|clothes|amazon|flipkart""",
            Category.Bills to """bills?|recharges?|utilities|electricity""",
            Category.Home to """rent|home|house|maintenance""",
            Category.Entertainment to """entertainment|movies?|subscriptions?|netflix""",
            Category.Health to """health|fitness|gym|medical|medicines?|doctors?|pharmacy""",
            Category.PersonalCare to """personal\s+care|salon|haircuts?|grooming""",
            Category.Education to """education|courses?|books?|tuition|work\s+tools""",
            Category.Finance to """emis?|loans?|insurance|investments?|sips?|finance""",
            Category.Gifts to """gifts?|family|charity|donations?""",
        ).mapValues { (_, words) -> Regex("""\b($words)\b""", o) }
        private val delivery = Regex("""\bdelivery\b""", o)
        private val countWords = Regex("""\b(how\s+many|number\s+of|count|times)\b""", o)
        private val averageWords = Regex("""\b(average|avg|typical)\b""", o)
        private val topWords = Regex("""\b(top|most|biggest|where\s+did|which\s+(shop|shops|merchant|merchants|place|places))\b""", o)

        private val wordRegex = Regex("""[\p{L}\p{N}]+""")
        private val commonWords = setOf(
            "how", "much", "many", "did", "do", "does", "i", "me", "my", "we", "our", "you", "spend", "spent", "spending",
            "money", "pay", "paid", "payment", "payments", "on", "at", "in", "from", "to", "for", "of", "the", "a", "an",
            "and", "or", "vs", "versus", "than", "compared", "compare", "with", "by", "what", "whats", "which", "where",
            "when", "is", "was", "were", "are", "be", "total", "amount", "cost", "costs", "bill", "bills", "expense",
            "expenses", "order", "orders", "ordered", "buy", "bought", "purchase", "purchases", "time", "per", "more",
            "less", "show", "tell", "give", "get", "all", "rs", "inr", "rupees", "so", "far", "last", "this",
            "week", "month", "year", "day", "days", "past", "previous", "am", "doing", "it", "that", "there", "any",
            "mine", "us", "up", "out", "about", "only", "just", "still", "again", "every", "each", "please",
            "trend", "trends", "breakdown", "summary", "overview", "budget", "budgets", "spends", "can", "could", "would",
        )

        /** Words left after periods, categories and question words are taken out: candidate shop names. */
        fun unknownWords(question: String): List<String> {
            var rest = question
            val known = periodPhrases.map { it.second } + categoryWords.values + listOf(delivery, countWords, averageWords, topWords)
            known.forEach { rest = it.replace(rest, " ") }
            return wordRegex.findAll(rest.lowercase()).map { it.value }
                .filter { it.length >= 2 && it.any(Char::isLetter) && it !in commonWords }
                .distinct()
                .toList()
        }

        /** Periods a text talks about ("last week's" -> LAST_WEEK), e.g. to check a model's sentence. */
        fun periodsIn(text: String): Set<Period> = periodPhrases.filter { (_, regex) -> regex.containsMatchIn(text) }.map { it.first }.toSet()

        /** Lowercase words of expense titles ("Paytm*swiggy8291" -> paytm, swiggy8291). */
        fun titleWords(titles: List<String>): Set<String> =
            titles.flatMap { wordRegex.findAll(it.lowercase()).map { m -> m.value }.toList() }.filter { it.length >= 2 }.toSet()

        fun translate(question: String, titleWords: Set<String> = emptySet()): SpendQuery {
            // Periods in the order they're mentioned: "last month vs this month".
            val periods = periodPhrases
                .flatMap { (period, regex) -> regex.findAll(question).map { it.range.first to period } }
                .sortedBy { it.first }
                .map { it.second }
                .distinct()
            val found = categoryWords.filterValues { it.containsMatchIn(question) }.keys
            val isDelivery = delivery.containsMatchIn(question)
            // "food delivery" means Swiggy/Zomato orders, not groceries.
            val categories = if (isDelivery) found - Category.Groceries else found
            val delivered = if (Category.Food in categories && isDelivery) listOf("swiggy", "zomato") else emptyList()
            // Leftover words the user's own expenses are named with: "uber" matches "Uber", "swiggy" matches "swiggy8291".
            val named = unknownWords(question).filter { w -> titleWords.any { it == w || (w.length >= 4 && it.startsWith(w)) } }
            val merchants = (delivered + named).distinct().take(MAX_MERCHANTS)
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

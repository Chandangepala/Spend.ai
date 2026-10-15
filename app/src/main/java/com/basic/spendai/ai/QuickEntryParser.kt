package com.basic.spendai.ai

import com.basic.spendai.data.Category
import com.basic.spendai.sms.CategoryGuesser
import kotlinx.serialization.Serializable

data class QuickEntryItem(val title: String, val amount: Double, val category: Category)

/**
 * Turns a note like "200 chai and samosa with team, 450 auto" into expenses to confirm.
 * The model only splits and labels; every amount it returns must be a number the user wrote.
 */
object QuickEntryParser : AiTask<String, List<QuickEntryItem>>() {
    override val maxTokens = 256

    @Serializable
    private data class Reply(val title: String = "", val amount: Double = 0.0, val category: String = "")

    override fun prompt(input: String) = """
        |Split this spending note into separate expenses. Reply with JSON only, no other text:
        |[{"title": "<short title>", "amount": <number>, "category": "<one of: $CATEGORY_NAMES>"}]
        |Use only amounts written in the note. Never add, multiply or invent amounts.
        """.trimMargin() + "\nNote: " + input.take(MAX_INPUT_CHARS)

    override fun parse(reply: String, input: String): List<QuickEntryItem>? {
        val items = AiJson.decode<List<Reply>>(reply) ?: return null
        val written = Rules.amountsIn(input)
        if (items.isEmpty() || items.size > written.size) return null
        // One bad amount means the model wasn't reading the note; distrust the whole reply.
        if (items.any { item -> written.none { it.sameAs(item.amount) } }) return null
        return items.map { item ->
            val title = item.title.trim().take(MAX_TITLE_CHARS).ifBlank { "Expense" }
            QuickEntryItem(title, item.amount, categoryOrNull(item.category) ?: Rules.categoryFor(title))
        }
    }

    override fun fallback(input: String) = Rules.parse(input)

    private fun Double.sameAs(other: Double) = kotlin.math.abs(this - other) < 0.005

    private const val MAX_INPUT_CHARS = 400
    private const val MAX_TITLE_CHARS = 40

    /** The no-AI path: split on commas / "and", pair each number with the words around it. */
    object Rules {
        // "1,200" and "1,00,000" are one number, not a list separator.
        private val groupingComma = Regex("""(?<=\d),(?=\d{2,3}(?!\d))""")
        private val numberRegex = Regex("""(?:₹|\brs\.?|\binr\b)?\s*(\d+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)
        private val segmentSplit = Regex("""\s*(?:[,;\n+]|\bthen\b)\s*""", RegexOption.IGNORE_CASE)
        private val andSplit = Regex("""\s+(?:and|&)\s+""", RegexOption.IGNORE_CASE)
        private val fillerWords = Regex("""^(?:for|on|at|spent|paid|rs\.?|inr|and|&|-)\s+|\s+(?:for|on|at|rs\.?|inr|-)$""", RegexOption.IGNORE_CASE)

        fun amountsIn(text: String): List<Double> =
            numberRegex.findAll(normalize(text)).mapNotNull { it.groupValues[1].toDoubleOrNull() }.filter { it > 0 }.toList()

        fun parse(text: String): List<QuickEntryItem> {
            val items = mutableListOf<Pair<String, Double>>()
            for (segment in segmentSplit.split(normalize(text)).filter { it.isNotBlank() }) {
                // "lunch 350 and coffee 120" holds two spends; "200 chai and samosa" holds one.
                val pieces = if (amountsIn(segment).size > 1) andSplit.split(segment) else listOf(segment)
                for (piece in pieces) {
                    val amount = amountsIn(piece).firstOrNull()
                    val words = cleanTitle(numberRegex.replace(piece, " "))
                    when {
                        amount != null -> items += words to amount
                        // Words without a number belong to the item before them.
                        items.isNotEmpty() && words.isNotEmpty() ->
                            items[items.lastIndex] = items.last().let { (title, a) -> "$title and $words".trim() to a }
                    }
                }
            }
            return items.map { (words, amount) ->
                val title = words.replaceFirstChar(Char::uppercase).take(MAX_TITLE_CHARS).ifBlank { "Expense" }
                QuickEntryItem(title, amount, categoryFor(title))
            }
        }

        fun categoryFor(title: String): Category = CategoryGuesser.guessCasual(title)

        private fun normalize(text: String) = groupingComma.replace(text, "")

        private fun cleanTitle(raw: String): String {
            var title = raw.replace(Regex("""\s+"""), " ").trim().trim('.', ':', '-', ' ')
            repeat(2) { title = fillerWords.replace(title, "").trim() }
            return title
        }
    }
}

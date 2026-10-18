package com.basic.spendai.ai

import com.basic.spendai.data.Category
import com.basic.spendai.sms.CategoryGuesser

data class QuickEntryItem(val title: String, val amount: Double, val category: Category)

/**
 * Turns a note like "200 chai and samosa with team, 450 auto" into expenses to confirm.
 * The rules split the note and read every amount (exact, and what users wrote). The model only
 * picks categories for items the rules couldn't place ("popcorn" -> Dining), so it can improve
 * the result but never lose an item or change an amount. Notes the rules fully understand never
 * reach the model.
 */
object QuickEntryParser : AiTask<String, List<QuickEntryItem>>() {
    override val maxTokens = 48

    override fun needsModel(input: String) = unsure(Rules.parse(input)).isNotEmpty()

    // A small model copies what it's shown, so the prompt is worked examples it only has to continue:
    // it ends on "<first item> ->", leaving just a category name to write.
    override fun prompt(input: String): String {
        val items = Rules.parse(input)
        val titles = unsure(items).map { items[it].title }
        return """
            |Choose the best category for each expense. Categories: $CATEGORY_NAMES.
            |
            |Expenses: Popcorn; Zepto order
            |Popcorn -> Dining & Drinks
            |Zepto order -> Groceries
            |
            |Expenses: Gift for mom
            |Gift for mom -> Gifts & Family
            |
            |Expenses: Plumber; Haircut; Bus pass
            |Plumber -> Home & Rent
            |Haircut -> Personal Care
            |Bus pass -> Transport
            |
            |Expenses: ${titles.joinToString("; ")}
            |${titles.first()} ->
            """.trimMargin()
    }

    override fun parse(reply: String, input: String): List<QuickEntryItem>? {
        val items = Rules.parse(input)
        val unsure = unsure(items)
        // Line by line, in order; one label off the list means the model wasn't following.
        val picked = AiLines.answers(reply, items[unsure.first()].title, unsure.size)
            ?.map { categoryOrNull(it) ?: return null } ?: return null
        val result = items.toMutableList()
        unsure.zip(picked).forEach { (i, category) -> result[i] = result[i].copy(category = category) }
        // Nothing placed (all "Other") means the model added nothing: show it as the rules' result.
        return result.takeIf { it != items }
    }

    override fun fallback(input: String) = Rules.parse(input)

    /** Indexes of the items the keyword rules couldn't place. */
    private fun unsure(items: List<QuickEntryItem>) = items.indices.filter { items[it].category == Category.Other }

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

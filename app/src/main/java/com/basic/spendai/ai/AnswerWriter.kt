package com.basic.spendai.ai

import com.basic.spendai.data.AppCurrency
import com.basic.spendai.stats.Metric
import com.basic.spendai.stats.Period
import com.basic.spendai.stats.PeriodStats
import com.basic.spendai.stats.SpendAnswer
import com.basic.spendai.util.formatCurrency
import kotlin.math.roundToInt

/**
 * Phrases computed results as a friendly sentence. The model only gets the facts below and may not
 * introduce any number that isn't in them (or in the question); otherwise a template is used.
 */
object AnswerWriter : AiTask<AnswerWriter.Input, String>() {
    data class Input(val question: String, val answer: SpendAnswer, val currency: AppCurrency)

    override val maxTokens = 96

    override fun prompt(input: Input) = """
        |Answer the user's question in one or two short, friendly sentences, using only these facts.
        |Do not add, round differently, or calculate any numbers. No advice.
        |Facts:
        |${facts(input)}
        """.trimMargin() + "\nQuestion: " + input.question.take(300)

    override fun parse(reply: String, input: Input): String? {
        val text = reply.trim().trim('"').takeIf { it.length in 10..MAX_REPLY_CHARS } ?: return null
        val answer = input.answer
        // A true number in the wrong frame misleads: "Last week's bill was ₹660" for a 30-day average.
        val periods = setOfNotNull(answer.primary.period, answer.comparison?.period)
        // "So far"/"ever" read as all-time in questions but are just wording in a sentence.
        if (!periods.containsAll(SpendQueryTranslator.Rules.periodsIn(text) - Period.ALL_TIME)) return null
        if (answer.query.metric == Metric.AVERAGE && !averageWords.containsMatchIn(text)) return null
        // "four times" must be checked like "4 times"; big number words can't be checked at all.
        if (bigNumberWords.containsMatchIn(text)) return null
        return text.takeIf { NumberCheck.onlyKnownNumbers(digitsForWords(it), facts(input) + " " + input.question) }
    }

    private val averageWords = Regex("""\b(average|avg|on average|per expense)\b""", RegexOption.IGNORE_CASE)
    private val bigNumberWords = Regex("""\b(hundred|thousand|lakh|lakhs|crore|crores|million|dozen)\b""", RegexOption.IGNORE_CASE)
    private val smallNumberWords = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve",
        "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen", "twenty",
    )
    private val numberWordRegex = Regex("""\b(${smallNumberWords.joinToString("|")}|once|twice)\b""", RegexOption.IGNORE_CASE)

    /** "four times" -> "4 times", "twice" -> "2", so the number check sees them. */
    internal fun digitsForWords(text: String) = numberWordRegex.replace(text) { m ->
        when (val w = m.value.lowercase()) {
            "once" -> "1"
            "twice" -> "2"
            else -> smallNumberWords.indexOf(w).toString()
        }
    }

    override fun fallback(input: Input) = template(input)

    /** Plain facts, one per line; also the only numbers the model may use. */
    fun facts(input: Input): String {
        val (_, answer, currency) = input
        fun money(v: Double) = formatCurrency(v, currency)
        val scope = answer.query.scope()
        // Only the numbers that answer this kind of question: given total, count and average every
        // time, a small model presented the average as the total (both real, so the check passed).
        fun line(s: PeriodStats) = "${s.period.label}${scope?.let { " ($it)" }.orEmpty()}: " + when (answer.query.metric) {
            Metric.TOTAL -> "total ${money(s.total)}"
            Metric.COUNT -> "${s.count} ${if (s.count == 1) "expense" else "expenses"}"
            Metric.AVERAGE -> if (s.count == 0) "no expenses" else "average ${money(s.average)} per expense, ${s.count} expenses"
            Metric.TOP_MERCHANTS -> if (s.topMerchants.isEmpty()) "no expenses" else
                "top: " + s.topMerchants.joinToString(", ") { (name, amount) -> "$name ${money(amount)}" }
        }
        return buildList {
            add(line(answer.primary))
            answer.comparison?.let { c ->
                add(line(c))
                if (answer.query.metric == Metric.TOTAL) {
                    change(answer.primary.total, c.total)?.let { add("Change: ${answer.primary.period.label} is $it than ${c.period.label}") }
                }
            }
        }.joinToString("\n")
    }

    /** The no-AI sentence. */
    fun template(input: Input): String {
        val (_, answer, currency) = input
        fun money(v: Double) = formatCurrency(v, currency)
        val p = answer.primary
        val c = answer.comparison
        val what = answer.query.categories.takeIf { it.isNotEmpty() }?.joinToString(" and ") { it.label }
        val on = what?.let { " on $it" }.orEmpty()
        return when (answer.query.metric) {
            Metric.COUNT -> buildString {
                append("${p.period.label}: ${p.count} ${if (p.count == 1) "expense" else "expenses"}$on.")
                c?.let { append(" ${it.period.label}: ${it.count}.") }
            }
            Metric.AVERAGE -> buildString {
                if (p.count == 0) append("No expenses$on ${p.period.label.lowercase()}.")
                else append("${p.period.label}, your average expense$on was ${money(p.average)} across ${p.count}.")
                c?.takeIf { it.count > 0 }?.let { append(" ${it.period.label}: ${money(it.average)}.") }
            }
            Metric.TOP_MERCHANTS -> if (p.topMerchants.isEmpty()) {
                "No expenses$on ${p.period.label.lowercase()}."
            } else {
                "Top$on ${p.period.label.lowercase()}: " + p.topMerchants.joinToString(", ") { (name, amount) -> "$name ${money(amount)}" } + "."
            }
            Metric.TOTAL -> buildString {
                fun spent(v: Double) = if (v == 0.0) "nothing" else money(v)
                append("You spent ${spent(p.total)}$on ${p.period.label.lowercase()}")
                if (c != null) {
                    append(", vs ${spent(c.total)} ${c.period.label.lowercase()}")
                    change(p.total, c.total)?.let { append(" (${p.period.label.lowercase()} was $it)") }
                }
                append(".")
            }
        }
    }

    /** "26% less" / "12% more" / "the same"; null when either side is zero ("100% less" helps nobody). */
    private fun change(current: Double, previous: Double): String? {
        if (previous == 0.0 || current == 0.0) return null
        val pct = ((current - previous) / previous * 100).roundToInt()
        return when {
            pct > 0 -> "$pct% more"
            pct < 0 -> "${-pct}% less"
            else -> "the same"
        }
    }

    private const val MAX_REPLY_CHARS = 300
}

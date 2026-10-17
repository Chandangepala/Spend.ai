package com.basic.spendai.ai

import com.basic.spendai.budgets.GoalPlan
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.util.formatCurrency
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One coaching sentence about a budget or goal. The code computes every number ([facts]); the
 * model may only reword them, never add numbers or give financial advice. Otherwise the [template].
 */
object CoachWriter : AiTask<CoachWriter.Input, String>() {

    sealed interface Topic {
        /** Why [suggested] is a realistic budget; [months] are the last complete months, oldest first. */
        data class Suggestion(val category: Category, val suggested: Double, val months: List<Double>) : Topic

        /** What cutting [category] by [percent]% saves. */
        data class WhatIf(val category: Category, val percent: Int, val averageMonth: Double, val monthly: Double, val yearly: Double) : Topic

        /** What it takes to finish a goal. */
        data class Goal(val name: String, val plan: GoalPlan, val deadline: LocalDate?) : Topic
    }

    data class Input(val topic: Topic, val currency: AppCurrency)

    override val maxTokens = 90

    override fun prompt(input: Input) = """
        |Rewrite these facts as one or two short, encouraging sentences for a personal spending app.
        |Use only these facts. Do not add, round differently, or calculate numbers. No investment,
        |loan, tax or credit advice.
        |Facts:
        |${facts(input).joinToString("\n")}
        """.trimMargin()

    override fun parse(reply: String, input: Input): String? {
        val text = reply.trim().trim('"').takeIf { it.length in 15..MAX_CHARS } ?: return null
        val factText = facts(input).joinToString(" ")
        if (AdviceGuard.givesAdvice(text, factText)) return null
        return text.takeIf { NumberCheck.onlyKnownNumbers(it, factText) }
    }

    override fun fallback(input: Input) = template(input)

    fun facts(input: Input): List<String> {
        fun money(v: Double) = formatCurrency(v, input.currency)
        return when (val t = input.topic) {
            is Topic.Suggestion -> listOf(
                "${t.category.label} spending in the last ${t.months.size} months: ${t.months.joinToString(", ") { money(it) }}.",
                "Typical month (median): ${money(t.months.sorted()[t.months.size / 2])}.",
                "Suggested monthly budget: ${money(t.suggested)}.",
            )
            is Topic.WhatIf -> listOf(
                "${t.category.label} averages ${money(t.averageMonth)} a month.",
                "Cutting it by ${t.percent}% saves ${money(t.monthly)} a month, ${money(t.yearly)} a year.",
            )
            is Topic.Goal -> buildList {
                val p = t.plan
                add("Goal: ${t.name}. Saved ${money(p.saved)} of ${money(p.target)}; ${money(p.remaining)} to go.")
                if (p.done) add("The goal is reached.")
                if (t.deadline != null && p.perMonth != null && p.monthsLeft != null) {
                    add("Due ${t.deadline.format(dateFormat)}: ${money(p.perMonth)} a month for ${p.monthsLeft} ${months(p.monthsLeft)}.")
                }
            }
        }
    }

    fun template(input: Input): String {
        fun money(v: Double) = formatCurrency(v, input.currency)
        return when (val t = input.topic) {
            is Topic.Suggestion -> {
                val median = t.months.sorted()[t.months.size / 2]
                "${t.category.label} has been about ${money(median)} in a typical month, so ${money(t.suggested)} is a realistic budget."
            }
            is Topic.WhatIf ->
                "Cutting ${t.category.label} by ${t.percent}% would save about ${money(t.monthly)} a month — ${money(t.yearly)} a year."
            is Topic.Goal -> {
                val p = t.plan
                when {
                    p.done -> "You've reached ${t.name}. Well done!"
                    t.deadline != null && p.perMonth != null && p.monthsLeft != null ->
                        "Put aside ${money(p.perMonth)} a month to reach ${t.name} by ${t.deadline.format(dateFormat)}."
                    else -> "${money(p.remaining)} to go for ${t.name}."
                }
            }
        }
    }

    private fun months(n: Int) = if (n == 1) "month" else "months"

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

    private const val MAX_CHARS = 260
}

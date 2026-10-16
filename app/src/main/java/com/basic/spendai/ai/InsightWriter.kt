package com.basic.spendai.ai

import com.basic.spendai.data.AppCurrency
import com.basic.spendai.insights.Finding
import com.basic.spendai.insights.WeeklyReport
import com.basic.spendai.util.formatCurrency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Writes the weekly summary from computed findings. The model gets only [facts] and may not add
 * numbers or give financial advice; otherwise the [template] is used.
 */
object InsightWriter : AiTask<InsightWriter.Input, String>() {
    data class Input(val report: WeeklyReport, val currency: AppCurrency, val zone: ZoneId = ZoneId.systemDefault())

    override val maxTokens = 160

    override fun prompt(input: Input) = """
        |Write a short, friendly summary of this person's spending week: at most 3 sentences, then one
        |practical tip about spending habits. Use only these facts. Do not add, round differently, or
        |calculate numbers. No investment, loan, tax or credit advice.
        |Facts:
        |${facts(input).joinToString("\n")}
        """.trimMargin()

    override fun parse(reply: String, input: Input): String? {
        val text = reply.trim().trim('"').takeIf { it.length in 20..MAX_CHARS } ?: return null
        val factText = facts(input).joinToString(" ")
        // "Loan" is fine when it's the name of a payment ("HDFC Loan EMI"), not when the model brings it up.
        if (adviceWords.findAll(text).any { !factText.contains(it.value, ignoreCase = true) }) return null
        return text.takeIf { NumberCheck.onlyKnownNumbers(it, factText) }
    }

    override fun fallback(input: Input) = template(input)

    /** One plain line per fact; these are the only numbers the model may use. */
    fun facts(input: Input): List<String> {
        val (report, currency) = input
        fun money(v: Double) = formatCurrency(v, currency)
        return buildList {
            add("Week ${range(input)}: spent ${money(report.total)} across ${report.count} ${if (report.count == 1) "expense" else "expenses"}.")
            report.findings.forEach { f ->
                add(
                    when (f) {
                        is Finding.CategoryVsUsual ->
                            "${f.category.label}: ${money(f.thisWeek)} this week vs a usual ${money(f.usualWeek)} " +
                                "(${abs(f.percent)}% ${if (f.percent >= 0) "more" else "less"})."
                        is Finding.WeekendSpike ->
                            "Weekends: ${money(f.weekendPerDay)} per day vs ${money(f.weekdayPerDay)} per weekday " +
                                "(${ratio(f.ratio)} times as much)."
                        is Finding.Subscriptions ->
                            "Repeating monthly payments: " + f.items.joinToString(", ") { "${it.merchant} ${money(it.amount)}" } +
                                " (${money(f.monthlyTotal)} a month)."
                        is Finding.LargeSpend ->
                            "Unusually large: ${f.title} ${money(f.amount)} (a usual ${f.category.label} payment is ${money(f.typical)})."
                    }
                )
            }
        }
    }

    /** The no-AI summary: the week's total, up to two findings, and one rule-picked tip. */
    fun template(input: Input): String {
        val (report, currency) = input
        fun money(v: Double) = formatCurrency(v, currency)
        if (report.count == 0) return "No spending recorded this week."
        val lines = facts(input)
        val findingLines = lines.drop(1).take(2)
        // No dates here: the card header and the notification title show the week.
        val opener = "You spent ${money(report.total)} across ${report.count} ${if (report.count == 1) "expense" else "expenses"}."
        if (findingLines.isEmpty()) return "$opener A steady week with nothing unusual."
        return (listOf(opener) + findingLines + tip(report, input)).joinToString(" ")
    }

    private fun tip(report: WeeklyReport, input: Input): String {
        val f = report.findings
        f.filterIsInstance<Finding.WeekendSpike>().firstOrNull()?.let { return "Tip: plan one low-spend day next weekend." }
        f.filterIsInstance<Finding.CategoryVsUsual>().firstOrNull { it.percent > 0 }?.let {
            return "Tip: try keeping ${it.category.label} near your usual ${formatCurrency(it.usualWeek, input.currency)} next week."
        }
        f.filterIsInstance<Finding.Subscriptions>().firstOrNull()?.let { return "Tip: check you still use every repeating payment." }
        return "Tip: double-check that big one-off payments were planned."
    }

    /** "Oct 11 – Oct 17". */
    fun range(input: Input): String {
        val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
        val from = Instant.ofEpochMilli(input.report.from).atZone(input.zone).toLocalDate()
        val to = Instant.ofEpochMilli(input.report.until - 1).atZone(input.zone).toLocalDate()
        return "${from.format(fmt)} – ${to.format(fmt)}"
    }

    private fun ratio(r: Double) = if (r == r.roundToInt().toDouble()) r.roundToInt().toString() else r.toString()

    // Coaching only: replies that drift into financial products are rejected.
    private val adviceWords = Regex("""\b(invest\w*|stocks?|shares|mutual funds?|crypto\w*|loans?|credit (score|card)s?|tax(es)?)\b""", RegexOption.IGNORE_CASE)

    private const val MAX_CHARS = 420
}

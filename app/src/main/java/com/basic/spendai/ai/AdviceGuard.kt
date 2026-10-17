package com.basic.spendai.ai

/** Coaching only: model replies that drift into financial products are rejected. */
object AdviceGuard {
    private val adviceWords =
        Regex("""\b(invest\w*|stocks?|shares|mutual funds?|crypto\w*|loans?|credit (score|card)s?|tax(es)?)\b""", RegexOption.IGNORE_CASE)

    /**
     * True if [text] brings up a financial product the [facts] didn't already mention. "Loan" is
     * fine when it's the name of a payment ("HDFC Loan EMI"), not when the model brings it up.
     */
    fun givesAdvice(text: String, facts: String): Boolean =
        adviceWords.findAll(text).any { !facts.contains(it.value, ignoreCase = true) }
}

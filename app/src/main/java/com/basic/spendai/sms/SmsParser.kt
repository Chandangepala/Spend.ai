package com.basic.spendai.sms

data class ParsedTransaction(
    val amount: Double,
    /** Cleaned name for display, e.g. "Swiggy". */
    val merchant: String?,
    /** The matching text as it appeared in the SMS, e.g. "PAYTM*SWIGGY8291". */
    val rawMerchant: String? = merchant,
)

/** Pulls the amount and merchant out of an Indian bank / UPI debit SMS. Returns null for anything else. */
object SmsParser {
    private val options = setOf(RegexOption.IGNORE_CASE)

    private val amountRegex = Regex("""(?:INR|Rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""", options)
    // SBI style: "A/C X1234 debited by 20.0 on ..." with no currency marker.
    private val bareAmountRegex = Regex("""\bdebited\s+(?:by|with|for)\s+([\d,]+(?:\.\d{1,2})?)""", options)
    private val debitRegex =
        Regex("""\b(debited|debit|spent|paid|sent|withdrawn|withdrawal|purchase[d]?|txn of)\b""", options)
    private val creditRegex = Regex("""\b(credited|received|deposited)\b""", options)
    private val rejectRegex = Regex(
        """\b(otp|one time password|will be debited|is due|due on|due date|payment due|requested|request|""" +
            """failed|declined|reversed|refund(ed)?|cashback)\b""",
        options,
    )
    // Autopay / mandate *set-up* notices quote the limit ("amount INR 75,000") rather than a spend.
    // Both parts are required so real autopay debits ("₹199 debited for autopay") still count.
    private val mandateRegex = Regex("""\b(auto[\s-]?pay|e-?mandate|mandate|standing instruction)\b""", options)
    private val setupRegex = Regex("""\b(activated|registered|created|set up|setup)\b""", options)
    private val accountHintRegex = Regex("""a/c|\bacct\b|account|\bcard\b|\bx+\d{2,}|\*\d{3,}|\bupi\b|\bvpa\b""", options)

    // Tried in order; the first that yields something name-like wins.
    private val merchantPatterns = listOf(
        Regex("""\bUPI/P2[AM]/\d+/([^/\n]+)""", options),
        Regex("""\b(?:VPA|to)\s+([\w.\-]+)@""", options),
        Regex("""\bat\s+([A-Za-z].*)""", options),
        Regex(""";\s*([A-Za-z][^;]*?)\s+credited""", options),
        Regex("""\bto\s+([A-Za-z].*)""", options),
        Regex("""\bInfo:?\s*([A-Za-z].*)""", options),
    )
    private val merchantEnd = Regex(
        """\s+(on|ref|refno|avl|avbl|bal|via|using|upi|from|txn|if|not|call|for|with|thru)\b|[;,(\n]|\.(\s|$)""",
        options,
    )
    private val notAMerchant = Regex("""^(a/c|ac|acct|account|your|card|xx|\*)""", options)

    fun parse(body: String): ParsedTransaction? {
        if (rejectRegex.containsMatchIn(body)) return null
        if (mandateRegex.containsMatchIn(body) && setupRegex.containsMatchIn(body)) return null
        val debit = debitRegex.find(body) ?: return null
        // "Acct debited ...; AMAZON credited" is a debit, "credited ... by a/c debited" is not.
        val credit = creditRegex.find(body)
        if (credit != null && credit.range.first < debit.range.first) return null
        if (!accountHintRegex.containsMatchIn(body)) return null

        val amountMatch = amountRegex.find(body) ?: bareAmountRegex.find(body) ?: return null
        val amount = amountMatch.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val (merchant, rawMerchant) = findMerchant(body) ?: (null to null)
        return ParsedTransaction(amount, merchant, rawMerchant)
    }

    /** (cleaned, raw) for the first pattern that yields something name-like. */
    private fun findMerchant(body: String): Pair<String, String>? {
        for (pattern in merchantPatterns) {
            val match = pattern.find(body)?.groupValues?.get(1) ?: continue
            val raw = (merchantEnd.find(match)?.let { match.substring(0, it.range.first) } ?: match).trim()
            val cleaned = cleanMerchant(raw) ?: continue
            return cleaned to raw.take(60)
        }
        return null
    }

    private fun cleanMerchant(cut: String): String? {
        val name = cut.replace(Regex("""\s+"""), " ").trim().take(30).trim()
        if (name.length < 2 || name.none { it.isLetter() } || notAMerchant.containsMatchIn(name)) return null
        // "AMAZON PAY" / "swiggy" read better as "Amazon Pay" / "Swiggy"; leave mixed-case names alone.
        return if (name == name.uppercase() || name == name.lowercase()) {
            name.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
        } else {
            name
        }
    }
}

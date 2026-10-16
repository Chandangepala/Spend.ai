package com.basic.spendai.ai

import com.basic.spendai.data.Category
import com.basic.spendai.sms.CategoryGuesser
import kotlinx.serialization.Serializable
import kotlin.math.abs

/** What a scanned receipt gives us: the bill as one expense, and line items when they add up. */
data class ReceiptResult(
    val merchant: String?,
    /** The bill total as printed; null when no total line could be found. */
    val total: Double?,
    val category: Category,
    /** Empty unless the items were validated against the receipt text and sum to the total. */
    val items: List<QuickEntryItem>,
)

/**
 * Turns OCR text from a receipt into a total and (optionally) line items. Code finds the total
 * line; the model only splits items, and every price it returns must be printed on the receipt.
 */
object ReceiptItemizer : AiTask<String, ReceiptResult>() {
    override val maxTokens = 512

    @Serializable
    private data class Reply(
        val merchant: String = "",
        val items: List<Item> = emptyList(),
        val total: Double? = null,
    )

    @Serializable
    private data class Item(val name: String = "", val amount: Double = 0.0, val category: String = "")

    override fun prompt(input: String) = """
        |This is text read from a shop receipt. Reply with JSON only, no other text:
        |{"merchant": "<shop name>", "items": [{"name": "<item>", "amount": <price>, "category": "<one of: $CATEGORY_NAMES>"}], "total": <bill total>}
        |Use only prices printed on the receipt. Leave out taxes, discounts and subtotals from items.
        """.trimMargin() + "\nReceipt:\n" + input.take(MAX_RECEIPT_CHARS)

    override fun parse(reply: String, input: String): ReceiptResult? {
        val decoded = AiJson.decode<Reply>(reply) ?: return null
        val printed = Rules.amountsIn(input)
        fun isPrinted(amount: Double) = printed.any { abs(it - amount) < 0.005 }

        val rulesTotal = Rules.findTotal(input)
        val aiTotal = decoded.total?.takeIf { it > 0 && isPrinted(it) }
        // The total line found by code wins; a model total is only used when code found none.
        val total = rulesTotal ?: aiTotal ?: return null
        val merchant = decoded.merchant.trim().takeIf { it.length in 2..40 && it.any(Char::isLetter) } ?: Rules.findMerchant(input)

        val category = Rules.categoryFor(merchant, input)
        val items = decoded.items.take(MAX_ITEMS)
        val sum = items.sumOf { it.amount }
        fun near(target: Double) = abs(sum - target) <= target * SUM_TOLERANCE
        val subtotal = Rules.findSubtotal(input)
        // Items must all be printed prices and add up to the bill (±2%), or to the subtotal when the
        // bill adds tax/service on top. Otherwise we only offer the total.
        val itemsValid = items.isNotEmpty() &&
            items.all { it.amount > 0 && isPrinted(it.amount) && it.name.isNotBlank() } &&
            (near(total) || (subtotal != null && subtotal < total && near(subtotal)))
        val lineItems = if (!itemsValid) emptyList() else items.map { item ->
            val name = item.name.trim().take(MAX_NAME_CHARS)
            QuickEntryItem(name, item.amount, categoryOrNull(item.category) ?: CategoryGuesser.guessCasual(name))
        }
        // Computed by code, not the model: whatever tax/charges sit between the items and the total.
        val extras = Math.round((total - sum) * 100) / 100.0
        val withExtras = if (lineItems.isNotEmpty() && extras >= 0.01 && !near(total)) {
            lineItems + QuickEntryItem("Taxes & charges", extras, category)
        } else {
            lineItems
        }
        return ReceiptResult(merchant = merchant, total = total, category = category, items = withExtras)
    }

    override fun fallback(input: String): ReceiptResult {
        val merchant = Rules.findMerchant(input)
        return ReceiptResult(merchant, Rules.findTotal(input), Rules.categoryFor(merchant, input), emptyList())
    }

    private const val MAX_RECEIPT_CHARS = 2500
    private const val MAX_ITEMS = 30
    private const val MAX_NAME_CHARS = 40
    private const val SUM_TOLERANCE = 0.02

    /** The no-AI path: find the total line and the shop name by pattern. */
    object Rules {
        private val options = setOf(RegexOption.IGNORE_CASE)
        private val amountRegex = Regex("""(?<![\d.])(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)(?![\d.])""")

        // Strongest first: "Grand total" beats "Total"; "Subtotal"/"Total qty"/"Total GST" never count.
        private val totalLabels = listOf(
            Regex("""\b(grand\s*total|net\s*(amount|payable|total)|amount\s*payable|total\s*payable|to\s*pay|bill\s*amount)\b""", options),
            Regex("""\b(total\s*(amount|amt|due|rs|inr)?|amount\s*due)\b""", options),
        )
        private val notTotal = Regex(
            """\b(sub\s*-?\s*total|total\s*(qty|quantity|items?|savings?|saved|discount|tax|gst|cgst|sgst|mrp)|taxable)\b""",
            options,
        )
        private val notMerchant = Regex(
            """^(tax\s*invoice|invoice|bill|receipt|cash\s*memo|retail|welcome|thank|gstin|gst|fssai|date|time|phone|ph|tel|mob|order|table|token|kot|cashier)\b""",
            options,
        )

        fun amountsIn(text: String): List<Double> =
            amountRegex.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.toList()

        /** The amount on the strongest total line (the last such line if repeated), or on the line after it. */
        fun findTotal(text: String): Double? {
            val lines = text.lines().map { it.trim() }
            for (label in totalLabels) {
                for (i in lines.indices.reversed()) {
                    val line = lines[i]
                    if (!label.containsMatchIn(line) || notTotal.containsMatchIn(line)) continue
                    val afterLabel = line.substring(label.find(line)!!.range.last + 1)
                    val amount = lastAmount(afterLabel) ?: lines.getOrNull(i + 1)?.let(::lastAmount)
                    if (amount != null && amount > 0) return amount
                }
            }
            return null
        }

        private val subtotalLabel = Regex("""\b(sub\s*-?\s*total|taxable\s*(value|amount))\b""", options)

        fun findSubtotal(text: String): Double? = text.lines().map { it.trim() }.let { lines ->
            lines.indices.firstNotNullOfOrNull { i ->
                val match = subtotalLabel.find(lines[i]) ?: return@firstNotNullOfOrNull null
                lastAmount(lines[i].substring(match.range.last + 1)) ?: lines.getOrNull(i + 1)?.let(::lastAmount)
            }
        }

        /** The first line that reads like a name: has letters, isn't a header word, isn't mostly digits. */
        fun findMerchant(text: String): String? = text.lines()
            .map { it.trim().trim('*', '-', '=', ' ') }
            .take(MERCHANT_SEARCH_LINES)
            .firstOrNull { line ->
                line.length in 3..40 && line.count(Char::isLetter) >= 3 &&
                    line.count(Char::isDigit) < line.length / 3 && !notMerchant.containsMatchIn(line)
            }
            ?.let { name -> if (name == name.uppercase()) name.lowercase().split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) } else name }

        fun categoryFor(merchant: String?, text: String): Category =
            merchant?.let { CategoryGuesser.guessCasual(it) }?.takeIf { it != Category.Other }
                ?: CategoryGuesser.guess(null, text)

        private fun lastAmount(text: String): Double? =
            amountRegex.findAll(text).lastOrNull()?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

        private const val MERCHANT_SEARCH_LINES = 6
    }
}

package com.basic.spendai.ui.activity

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense

/**
 * Activity search: [query] matches the title or category name (case-insensitive), or an amount
 * ("250" finds ₹250 and ₹2,500.50 alike); [categories] narrows to those categories when not empty.
 */
fun filterExpenses(expenses: List<Expense>, query: String, categories: Set<Category>): List<Expense> {
    val q = query.trim()
    val digits = q.filter(Char::isDigit)
    // "250", "2,500.50" or "₹610" are amount searches; "uber 2" is a text search.
    val looksLikeAmount = digits.isNotEmpty() && q.all { it.isDigit() || it in ",.₹ " }
    return expenses.filter { e ->
        (categories.isEmpty() || e.category in categories) && (
            q.isEmpty() ||
                e.title.contains(q, ignoreCase = true) ||
                e.category.label.contains(q, ignoreCase = true) ||
                (looksLikeAmount && amountDigits(e.amount).contains(digits))
            )
    }
}

/** "2500.5" -> "250050"; "250.0" -> "250": the digits people type when looking for an amount. */
private fun amountDigits(amount: Double): String {
    val whole = amount.toLong()
    val paise = Math.round((amount - whole) * 100)
    return if (paise == 0L) whole.toString() else "$whole${"%02d".format(paise)}"
}

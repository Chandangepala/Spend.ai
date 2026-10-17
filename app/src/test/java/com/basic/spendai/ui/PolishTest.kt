package com.basic.spendai.ui

import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.activity.filterExpenses
import com.basic.spendai.ui.home.StripSegment
import com.basic.spendai.ui.home.stripSegments
import org.junit.Assert.assertEquals
import org.junit.Test

class PolishTest {

    private val expenses = listOf(
        Expense(1, "Swiggy dinner", 250.0, Category.Food),
        Expense(2, "Uber", 2500.5, Category.Transport),
        Expense(3, "Blinkit", 610.0, Category.Groceries),
        Expense(4, "Netflix", 649.0, Category.Entertainment),
    )

    private fun ids(list: List<Expense>) = list.map { it.id }

    @Test
    fun searchMatchesTitleCategoryAndAmount() {
        assertEquals(listOf(1L), ids(filterExpenses(expenses, "swig", emptySet())))
        assertEquals(listOf(1L), ids(filterExpenses(expenses, "dining", emptySet()))) // category label
        assertEquals(listOf(1L, 2L), ids(filterExpenses(expenses, "250", emptySet()))) // ₹250 and ₹2,500.50
        assertEquals(listOf(2L), ids(filterExpenses(expenses, "2,500.50", emptySet())))
        assertEquals(ids(expenses), ids(filterExpenses(expenses, "  ", emptySet())))
    }

    @Test
    fun categoryChipsNarrowAndCombineWithSearch() {
        assertEquals(listOf(3L, 4L), ids(filterExpenses(expenses, "", setOf(Category.Groceries, Category.Entertainment))))
        assertEquals(emptyList<Long>(), ids(filterExpenses(expenses, "uber", setOf(Category.Food))))
    }

    @Test
    fun stripKeepsTopThreeAndFoldsTheRest() {
        val shares = listOf(Category.Finance to 2597.5, Category.Food to 2585.0, Category.Bills to 2468.72, Category.Gifts to 1000.0, Category.Other to 2.0)
        val segments = stripSegments(shares)
        assertEquals(listOf("EMI & Finance", "Dining & Drinks", "Bills & Utilities", "2 more"), segments.map { it.label })
        assertEquals(StripSegment(null, 1002.0, "2 more"), segments.last())
        assertEquals(3, stripSegments(shares.take(3)).size) // nothing to fold
    }
}

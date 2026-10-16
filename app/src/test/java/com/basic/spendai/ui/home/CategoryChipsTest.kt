package com.basic.spendai.ui.home

import com.basic.spendai.data.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryChipsTest {

    @Test
    fun noUsageKeepsTheStandardOrder() {
        assertEquals(Category.entries.toList(), orderCategories(emptyMap()))
    }

    @Test
    fun mostUsedComeFirstAndOtherStaysLast() {
        val order = orderCategories(mapOf(Category.Other to 50, Category.Transport to 9, Category.Groceries to 4, Category.Gifts to 4))
        assertEquals(listOf(Category.Transport, Category.Groceries, Category.Gifts), order.take(3))
        assertEquals(Category.Other, order.last())
        assertEquals(Category.entries.size, order.size)
    }

    @Test
    fun sevenChipsShowBeforeMore() {
        val top = orderCategories(emptyMap()).filter { it != Category.Other }.take(VISIBLE_CATEGORIES)
        assertEquals(
            listOf(Category.Food, Category.Groceries, Category.Transport, Category.Shopping, Category.Bills, Category.Home, Category.Entertainment),
            top,
        )
    }
}

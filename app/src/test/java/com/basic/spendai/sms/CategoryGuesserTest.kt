package com.basic.spendai.sms

import com.basic.spendai.data.Category
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGuesserTest {

    @Test
    fun matchesMerchantKeywords() {
        assertEquals(Category.Food, CategoryGuesser.guess("Swiggy", ""))
        assertEquals(Category.Transport, CategoryGuesser.guess("Uber India", ""))
        assertEquals(Category.Shopping, CategoryGuesser.guess("Amazon Pay", ""))
        assertEquals(Category.Bills, CategoryGuesser.guess("Airtel Postpaid", ""))
        assertEquals(Category.Entertainment, CategoryGuesser.guess("Netflix", ""))
        assertEquals(Category.Health, CategoryGuesser.guess("Apollo Pharmacy", ""))
    }

    @Test
    fun foodDeliveryBeatsRideHailing() {
        assertEquals(Category.Food, CategoryGuesser.guess("Uber Eats", ""))
    }

    @Test
    fun keywordsMatchWholeWordsOnly() {
        // "ola" must not match inside "Motorola" or "Coca Cola".
        assertEquals(Category.Other, CategoryGuesser.guess("Motorola", ""))
    }

    @Test
    fun fallsBackToBodyThenOther() {
        assertEquals(Category.Transport, CategoryGuesser.guess(null, "Rs 500 spent on card XX12 for FASTAG recharge"))
        assertEquals(Category.Other, CategoryGuesser.guess("Raju Stall", "Rs 20 debited from A/c XX12"))
    }

    @Test
    fun learnedCategoryWins() {
        assertEquals(Category.Health, CategoryGuesser.guess("Amazon", "", learned = Category.Health))
    }

    @Test
    fun merchantKeyIgnoresCaseAndSpaces() {
        assertEquals("amazon pay", CategoryGuesser.merchantKey("  Amazon Pay "))
    }
}

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
    fun newCategoriesHaveTheirOwnKeywords() {
        assertEquals(Category.Groceries, CategoryGuesser.guess("Blinkit", ""))
        assertEquals(Category.Food, CategoryGuesser.guess("Zepto Cafe", ""))
        assertEquals(Category.Home, CategoryGuesser.guess("NoBroker Rent", ""))
        assertEquals(Category.Finance, CategoryGuesser.guess("HDFC Loan EMI", ""))
        assertEquals(Category.PersonalCare, CategoryGuesser.guess("Toni and Guy Salon", ""))
        assertEquals(Category.Travel, CategoryGuesser.guess("IRCTC", ""))
        assertEquals(Category.Education, CategoryGuesser.guess("Udemy", ""))
        assertEquals(Category.Gifts, CategoryGuesser.guess("Ferns N Petals", ""))
    }

    @Test
    fun casualNotesUseTheNewSplit() {
        assertEquals(Category.Groceries, CategoryGuesser.guessCasual("milk and bread"))
        assertEquals(Category.Food, CategoryGuesser.guessCasual("starbucks"))
        assertEquals(Category.Travel, CategoryGuesser.guessCasual("train to pune"))
        assertEquals(Category.Transport, CategoryGuesser.guessCasual("auto"))
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

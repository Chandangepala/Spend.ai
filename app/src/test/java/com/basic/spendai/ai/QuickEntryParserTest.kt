package com.basic.spendai.ai

import com.basic.spendai.ai.QuickEntryParser.Rules
import com.basic.spendai.data.Category
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryParserTest {

    // --- Rules path (no AI) ---

    @Test
    fun splitsOnCommasAndKeepsAndInsideOneItem() {
        assertEquals(
            listOf(
                QuickEntryItem("Chai and samosa with team", 200.0, Category.Food),
                QuickEntryItem("Auto", 450.0, Category.Transport),
            ),
            Rules.parse("200 chai and samosa with team, 450 auto"),
        )
    }

    @Test
    fun amountCanComeAfterTheWords() {
        assertEquals(listOf(QuickEntryItem("Auto", 450.0, Category.Transport)), Rules.parse("auto 450"))
    }

    @Test
    fun splitsOnAndWhenEachSideHasAnAmount() {
        assertEquals(
            listOf(
                QuickEntryItem("Lunch", 350.0, Category.Food),
                QuickEntryItem("Coffee", 120.0, Category.Food),
            ),
            Rules.parse("lunch 350 and coffee 120"),
        )
    }

    @Test
    fun currencyMarkersAndGroupingCommas() {
        assertEquals(listOf(QuickEntryItem("Groceries", 1200.0, Category.Groceries)), Rules.parse("₹1,200 groceries"))
        assertEquals(listOf(QuickEntryItem("Rent", 100000.0, Category.Home)), Rules.parse("rent Rs 1,00,000"))
        assertEquals(listOf(1200.0, 450.0), Rules.amountsIn("₹1,200 groceries, 450 auto"))
    }

    @Test
    fun decimalsAndFillerWords() {
        assertEquals(listOf(QuickEntryItem("Medicines", 89.5, Category.Health)), Rules.parse("spent 89.50 on medicines"))
    }

    @Test
    fun unknownWordsAreOtherAndNoNumbersMeansNothing() {
        assertEquals(listOf(QuickEntryItem("Raju", 60.0, Category.Other)), Rules.parse("60 paid raju"))
        assertTrue(Rules.parse("chai with team").isEmpty())
    }

    // --- AI path: the model only places items the rules filed as Other ---

    @Test
    fun modelPlacesOnlyTheUnknownItems() = runTest {
        // Rules know "movie tickets" (Entertainment); neither they nor the built-in model can place a
        // payment to a person, so only "Sonu" goes to the AI.
        val provider = FakeAiProvider(" Gifts & Family\n")
        val result = QuickEntryParser.run(provider, "movie tickets 600, sonu 250")
        assertTrue(result.fromAi)
        assertEquals(
            listOf(QuickEntryItem("Movie tickets", 600.0, Category.Entertainment), QuickEntryItem("Sonu", 250.0, Category.Gifts)),
            result.value,
        )
        assertTrue(provider.prompts.single().endsWith("Expenses: Sonu\nSonu ->"))
    }

    @Test
    fun notesTheRulesFullyUnderstandNeverReachTheModel() = runTest {
        val provider = FakeAiProvider("Shopping")
        val result = QuickEntryParser.run(provider, "200 chai and samosa with team, 450 auto")
        assertFalse(result.fromAi)
        assertTrue(provider.prompts.isEmpty())
        assertEquals(listOf(200.0, 450.0), result.value.map { it.amount })
    }

    @Test
    fun wrongCountOffListOrNoHelpKeepsTheRules() = runTest {
        val note = "movie tickets 600, popcorn 250"
        val rules = Rules.parse(note)
        // Extra lines after the answer are ignored; only the unknown item's line counts.
        assertEquals(Category.Food, QuickEntryParser.run(FakeAiProvider(" Dining & Drinks\nChips -> Groceries"), note).value[1].category)
        // A label that isn't a category (or the whole list echoed back).
        assertEquals(rules, QuickEntryParser.run(FakeAiProvider(" Snacks"), note).value)
        assertFalse(QuickEntryParser.run(FakeAiProvider(" Dining & Drinks, Groceries, Transport, Shopping"), note).fromAi)
        // "Other" adds nothing, so it isn't shown as an AI result.
        assertFalse(QuickEntryParser.run(FakeAiProvider(" Other"), note).fromAi)
        assertFalse(QuickEntryParser.run(FakeAiProvider(""), note).fromAi)
    }

    @Test
    fun amountsAndItemsAlwaysComeFromTheRules() = runTest {
        // Whatever the model says, it can't drop "450 auto" or change an amount any more.
        val note = "200 chai and samosa with team, 450 auto, 99 popcorn"
        val result = QuickEntryParser.run(FakeAiProvider(" Dining & Drinks"), note)
        assertEquals(listOf(200.0, 450.0, 99.0), result.value.map { it.amount })
        assertEquals(Category.Food, result.value[2].category)
    }
}

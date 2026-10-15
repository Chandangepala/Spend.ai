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
        assertEquals(listOf(QuickEntryItem("Groceries", 1200.0, Category.Food)), Rules.parse("₹1,200 groceries"))
        assertEquals(listOf(QuickEntryItem("Rent", 100000.0, Category.Bills)), Rules.parse("rent Rs 1,00,000"))
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

    // --- AI path ---

    @Test
    fun usesValidatedModelSplit() = runTest {
        val provider = FakeAiProvider(
            """[{"title":"Chai and samosa","amount":200,"category":"Food"},{"title":"Auto ride","amount":450,"category":"Transport"}]"""
        )
        val result = QuickEntryParser.run(provider, "200 chai and samosa with team, 450 auto")
        assertTrue(result.fromAi)
        assertEquals(listOf(200.0, 450.0), result.value.map { it.amount })
        assertEquals("Auto ride", result.value[1].title)
    }

    @Test
    fun inventedAmountRejectsTheWholeReply() = runTest {
        // 650 is a total the model computed; the user never wrote it.
        val provider = FakeAiProvider("""[{"title":"Chai and auto","amount":650,"category":"Food"}]""")
        val result = QuickEntryParser.run(provider, "200 chai, 450 auto")
        assertFalse(result.fromAi)
        assertEquals(listOf(200.0, 450.0), result.value.map { it.amount })
    }

    @Test
    fun offListCategoryUsesRulesForThatItem() = runTest {
        val provider = FakeAiProvider("""[{"title":"Auto","amount":450,"category":"Travel"}]""")
        val result = QuickEntryParser.run(provider, "450 auto")
        assertTrue(result.fromAi)
        assertEquals(Category.Transport, result.value.single().category)
    }

    @Test
    fun brokenOutputFallsBackToRules() = runTest {
        val result = QuickEntryParser.run(FakeAiProvider("Here are your expenses: chai and auto"), "200 chai, 450 auto")
        assertFalse(result.fromAi)
        assertEquals(2, result.value.size)
    }

    @Test
    fun moreItemsThanWrittenAmountsIsRejected() = runTest {
        val provider = FakeAiProvider(
            """[{"title":"Chai","amount":200,"category":"Food"},{"title":"Samosa","amount":200,"category":"Food"}]"""
        )
        assertFalse(QuickEntryParser.run(provider, "200 chai and samosa").fromAi)
    }
}

package com.basic.spendai.ai

import com.basic.spendai.ai.ReceiptItemizer.Rules
import com.basic.spendai.data.Category
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptItemizerTest {

    private val supermarket = """
        DMART
        Avenue Supermarts Ltd
        GSTIN 27AAACA1234B1Z5
        TAX INVOICE
        Date: 15/10/2026 18:42
        Rice 5kg 450.00
        Shampoo 220.00
        Notebook 570.00
        Total Qty: 3
        Sub Total 1240.00
        CGST 0.00
        Grand Total 1,240.00
        Total Savings 85.00
    """.trimIndent()

    private val restaurant = """
        CAFE COFFEE DAY
        Table 4
        Cappuccino 2 x 180.00 360.00
        Sandwich 1 x 220.00 220.00
        Sub-Total 580.00
        Service Charge 5% 29.00
        CGST 2.5% 14.50
        SGST 2.5% 14.50
        TOTAL 638.00
    """.trimIndent()

    private val pharmacy = """
        Welcome
        APOLLO PHARMACY
        Bill No: 1234
        Dolo 650 Tab 30.00
        Vicks 85.50
        Total GST 12.00
        Net Amount:
        Rs. 115.50
    """.trimIndent()

    // --- Rules path ---

    @Test
    fun findsGrandTotalNotSubtotalOrSavings() {
        assertEquals(1240.0, Rules.findTotal(supermarket)!!, 0.001)
        assertEquals(638.0, Rules.findTotal(restaurant)!!, 0.001)
    }

    @Test
    fun totalCanBeOnTheNextLineAndGstLineIsIgnored() {
        assertEquals(115.5, Rules.findTotal(pharmacy)!!, 0.001)
    }

    @Test
    fun noTotalLineMeansNoGuess() {
        assertNull(Rules.findTotal("CHAI POINT\nMasala chai 40.00\nThank you"))
    }

    @Test
    fun merchantIsFirstNameLikeLineAndDrivesCategory() = runTest {
        assertEquals("Dmart", Rules.findMerchant(supermarket))
        assertEquals("Apollo Pharmacy", Rules.findMerchant(pharmacy))
        assertEquals(Category.Shopping, ReceiptItemizer.run(RulesOnlyProvider, supermarket).value.category)
        assertEquals(Category.Food, Rules.categoryFor(Rules.findMerchant(restaurant), restaurant))
        assertEquals(Category.Health, Rules.categoryFor("Apollo Pharmacy", pharmacy))
    }

    // --- AI path ---

    @Test
    fun itemsThatAddUpAreOffered() = runTest {
        val provider = FakeAiProvider(
            """{"merchant":"DMart","items":[{"name":"Rice 5kg","amount":450,"category":"Food"},
            {"name":"Shampoo","amount":220,"category":"Shopping"},{"name":"Notebook","amount":570,"category":"Other"}],"total":1240}"""
        )
        val result = ReceiptItemizer.run(provider, supermarket)
        assertTrue(result.fromAi)
        assertEquals(1240.0, result.value.total!!, 0.001)
        assertEquals(listOf(450.0, 220.0, 570.0), result.value.items.map { it.amount })
        assertEquals(Category.Food, result.value.items[0].category)
    }

    @Test
    fun restaurantItemsMatchSubtotalAndCodeAddsTaxes() = runTest {
        val provider = FakeAiProvider(
            """{"merchant":"Cafe Coffee Day","items":[{"name":"Cappuccino","amount":360,"category":"Food"},
            {"name":"Sandwich","amount":220,"category":"Food"}],"total":638}"""
        )
        val items = ReceiptItemizer.run(provider, restaurant).value.items
        assertEquals(listOf("Cappuccino", "Sandwich", "Taxes & charges"), items.map { it.title })
        assertEquals(58.0, items.last().amount, 0.001)
        assertEquals(638.0, items.sumOf { it.amount }, 0.001)
    }

    @Test
    fun inventedPriceDropsItemsButKeepsTotal() = runTest {
        // 300 isn't printed anywhere on the receipt.
        val provider = FakeAiProvider(
            """{"merchant":"DMart","items":[{"name":"Rice","amount":450,"category":"Food"},
            {"name":"Shampoo","amount":300,"category":"Shopping"},{"name":"Notebook","amount":490,"category":"Other"}],"total":1240}"""
        )
        val result = ReceiptItemizer.run(provider, supermarket).value
        assertEquals(1240.0, result.total!!, 0.001)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun itemsThatDontAddUpGiveTotalOnly() = runTest {
        val provider = FakeAiProvider(
            """{"merchant":"DMart","items":[{"name":"Rice","amount":450,"category":"Food"}],"total":1240}"""
        )
        assertTrue(ReceiptItemizer.run(provider, supermarket).value.items.isEmpty())
    }

    @Test
    fun codeTotalBeatsModelTotal() = runTest {
        val provider = FakeAiProvider("""{"merchant":"DMart","items":[],"total":1325}""")
        assertEquals(1240.0, ReceiptItemizer.run(provider, supermarket).value.total!!, 0.001)
    }

    @Test
    fun brokenOutputFallsBackToRulesTotal() = runTest {
        val result = ReceiptItemizer.run(FakeAiProvider("I see a receipt from DMart"), supermarket)
        assertFalse(result.fromAi)
        assertEquals(1240.0, result.value.total!!, 0.001)
        assertEquals("Dmart", result.value.merchant)
    }
}

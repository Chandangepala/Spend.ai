package com.basic.spendai.ai

import com.basic.spendai.ai.SmsEnricher.Outcome
import com.basic.spendai.data.Category
import com.basic.spendai.data.CategoryCorrection
import com.basic.spendai.data.CorrectionSource
import com.basic.spendai.data.DetectedTransaction
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsEnricherTest {

    private fun detection(merchant: String? = "Paytm*swiggy8291", category: Category = Category.Other) = DetectedTransaction(
        id = 1,
        smsKey = "k",
        sender = "AX-AXISBK",
        body = "INR 250.00 debited A/c no. XX1234 UPI/P2M/628712345678/PAYTM*SWIGGY8291 Not you? Axis Bank",
        amount = 250.0,
        merchant = merchant,
        suggestedCategory = category,
        timestamp = 0,
    )

    private fun enricher(provider: AiProvider, learned: Map<String, Category> = emptyMap()) =
        SmsEnricher(provider) { learned[it] }

    @Test
    fun validReplyCleansMerchantAndCategory() = runTest {
        val provider = FakeAiProvider("""{"is_transaction": true, "merchant": "Swiggy", "category": "Food"}""")
        assertEquals(Outcome.Enriched("Swiggy", Category.Food, fromAi = true), enricher(provider).enrich(detection()))
        assertTrue(provider.prompts.single().contains("PAYTM*SWIGGY8291"))
    }

    @Test
    fun offListCategoryKeepsRulesGuess() = runTest {
        val provider = FakeAiProvider("""{"is_transaction": true, "merchant": "Swiggy", "category": "Groceries"}""")
        val outcome = enricher(provider).enrich(detection(category = Category.Shopping))
        assertEquals(Outcome.Enriched("Swiggy", Category.Shopping, fromAi = true), outcome)
    }

    @Test
    fun badMerchantKeepsRulesName() = runTest {
        val provider = FakeAiProvider("""{"is_transaction": true, "merchant": "8291", "category": "Food"}""")
        assertEquals(Outcome.Enriched("Paytm*swiggy8291", Category.Food, fromAi = true), enricher(provider).enrich(detection()))
    }

    @Test
    fun brokenJsonSettlesOnRules() = runTest {
        val outcome = enricher(FakeAiProvider("Sorry, I can't")).enrich(detection(category = Category.Food))
        assertEquals(Outcome.Enriched("Paytm*swiggy8291", Category.Food, fromAi = false), outcome)
    }

    @Test
    fun modelFailureLeavesRowForLater() = runTest {
        assertEquals(Outcome.ModelUnavailable, enricher(FakeAiProvider(null)).enrich(detection()))
    }

    @Test
    fun notATransactionIsHidden() = runTest {
        val provider = FakeAiProvider("""{"is_transaction": false, "merchant": "", "category": "Other"}""")
        assertEquals(Outcome.NotATransaction, enricher(provider).enrich(detection()))
    }

    @Test
    fun learnedCategorySkipsTheModel() = runTest {
        val provider = FakeAiProvider("""{"is_transaction": true, "merchant": "X", "category": "Bills"}""")
        val outcome = enricher(provider, mapOf("swiggy" to Category.Health)).enrich(detection(merchant = "Swiggy"))
        assertEquals(Outcome.Enriched("Swiggy", Category.Health, fromAi = false), outcome)
        assertTrue(provider.prompts.isEmpty())
    }

    @Test
    fun learnedCategoryAppliesToTheModelsCleanedName() = runTest {
        val provider = FakeAiProvider("""{"is_transaction": true, "merchant": "Swiggy", "category": "Food"}""")
        val outcome = enricher(provider, mapOf("swiggy" to Category.Health)).enrich(detection())
        assertEquals(Outcome.Enriched("Swiggy", Category.Health, fromAi = true), outcome)
    }

    @Test
    fun recentCorrectionsAreShownAsExamples() = runTest {
        val provider = FakeAiProvider("""{"is_transaction": true, "merchant": "Rakesh Kumar Singh", "category": "Food"}""")
        val examples = listOf(
            correction("Jagdish Vaishna", Category.Food),
            correction("Airtel", Category.Bills),
        )
        SmsEnricher(provider, examples) { null }.enrich(detection())
        val prompt = provider.prompts.single()
        assertTrue(prompt.contains("- Jagdish Vaishna -> Food"))
        assertTrue(prompt.contains("- Airtel -> Bills"))
    }

    @Test
    fun promptStaysShortWithAFullSetOfExamples() = runTest {
        val provider = FakeAiProvider(null)
        val examples = (1..10).map { correction("Merchant number $it with a long name", Category.Shopping) }
        SmsEnricher(provider, examples) { null }.enrich(detection())
        val prompt = provider.prompts.single()
        assertEquals(SmsEnricher.Task.MAX_EXAMPLES, prompt.lines().count { it.startsWith("- ") })
        // Nano takes < 4000 tokens; ~4 chars per token keeps this well under the 1500-token budget.
        assertTrue("prompt was ${prompt.length} chars", prompt.length < 2000)
    }

    @Test
    fun noExamplesMeansNoExamplesSection() = runTest {
        val provider = FakeAiProvider(null)
        enricher(provider).enrich(detection())
        assertFalse(provider.prompts.single().contains("own choices"))
    }

    private fun correction(merchant: String, chosen: Category) = CategoryCorrection(
        merchant = merchant,
        smsSnippet = "",
        suggested = Category.Other,
        chosen = chosen,
        source = CorrectionSource.RULES,
    )
}

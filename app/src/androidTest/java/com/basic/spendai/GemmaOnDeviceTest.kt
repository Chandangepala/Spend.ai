package com.basic.spendai

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.basic.spendai.ai.AiProviders
import com.basic.spendai.ai.AiTier
import com.basic.spendai.ai.CoachWriter
import com.basic.spendai.ai.GemmaModel
import com.basic.spendai.ai.QuickEntryParser
import com.basic.spendai.ai.SmsEnricher
import com.basic.spendai.ai.SpendQueryTranslator
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.DetectedTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the AI tasks through the real Gemma model installed on this phone (skipped if it isn't).
 * Uses in-memory samples only: no database, no UI. Asserts what the code guarantees (amounts
 * come from the input, results are valid either way) and logs what the model actually said,
 * tagged "GemmaOnDevice", for a human to judge.
 */
@RunWith(AndroidJUnit4::class)
class GemmaOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun needsModel() {
        assumeTrue("Gemma model not installed on this device", GemmaModel.isInstalled(context))
    }

    /** Runs off the main thread, the way the background workers call the model. */
    private fun <T> timed(label: String, block: suspend () -> T): T = runBlocking {
        withContext(Dispatchers.Default) {
            val start = System.nanoTime()
            val result = block()
            Log.i(TAG, "$label (${(System.nanoTime() - start) / 1_000_000} ms): $result")
            result
        }
    }

    @Test
    fun backgroundCallsGetGemma() {
        val provider = timed("background provider") { AiProviders.background(context) }
        assertEquals(AiTier.GEMMA, provider.tier)
    }

    @Test
    fun smsEnrichment() {
        val provider = runBlocking { AiProviders.background(context) }
        val enricher = SmsEnricher(provider) { null }
        val upi = detection(
            "Rs.349.00 debited from A/c XX4821 on 18-10-26 to VPA paytm-swiggy8291@ptys (UPI Ref 629184736512). Not you? Call 18001234",
            merchant = "Paytm-swiggy8291",
            category = Category.Other,
        )
        val outcome = timed("SMS: Swiggy UPI debit") { enricher.enrich(upi) }
        // The model ran (a background call with Nano would have come back unavailable).
        assertNotEquals(SmsEnricher.Outcome.ModelUnavailable, outcome)

        val promo = detection(
            "Get flat Rs 150 OFF on your next 3 orders! Use code FEAST150. Min order Rs 499. T&C apply.",
            merchant = null,
            category = Category.Other,
        )
        val promoOutcome = timed("SMS: promo") { enricher.enrich(promo) }
        assertNotEquals(SmsEnricher.Outcome.ModelUnavailable, promoOutcome)
        // No merchant is named in the promo, so none may be invented (the model once echoed "Swiggy").
        (promoOutcome as? SmsEnricher.Outcome.Enriched)?.let { assertEquals(null, it.merchant) }
    }

    @Test
    fun quickEntryAmountsAlwaysComeFromTheNote() {
        val provider = runBlocking { AiProviders.background(context) }
        val result = timed("Quick entry") { QuickEntryParser.run(provider, "200 chai and samosa with team, 450 auto") }
        assertEquals(listOf(200.0, 450.0), result.value.map { it.amount })
        val result2 = timed("Quick entry 2") { QuickEntryParser.run(provider, "lunch 350 and coffee 120, uber 230") }
        assertEquals(listOf(350.0, 120.0, 230.0), result2.value.map { it.amount })
    }

    @Test
    fun askUnderstandsTheQuestion() {
        val provider = runBlocking { AiProviders.background(context) }
        val result = timed("Ask: food last month vs this month") { SpendQueryTranslator().run(provider, "food last month vs this month") }
        assertTrue(result.value.toString(), result.value.categories.contains(Category.Food))
    }

    @Test
    fun coachRewordsOnlyComputedNumbers() {
        val provider = runBlocking { AiProviders.background(context) }
        val topic = CoachWriter.Topic.WhatIf(Category.Food, 20, 6100.0, 1220.0, 14640.0)
        val result = timed("Coach: what-if") { CoachWriter.run(provider, CoachWriter.Input(topic, AppCurrency.INR)) }
        assertTrue(result.value.isNotBlank())
    }

    private fun detection(body: String, merchant: String?, category: Category) = DetectedTransaction(
        smsKey = body.hashCode().toString(),
        sender = "AX-HDFCBK",
        body = body,
        amount = 349.0,
        merchant = merchant,
        rawMerchant = merchant,
        suggestedCategory = category,
        timestamp = System.currentTimeMillis(),
    )

    private companion object {
        const val TAG = "GemmaOnDevice"
    }
}

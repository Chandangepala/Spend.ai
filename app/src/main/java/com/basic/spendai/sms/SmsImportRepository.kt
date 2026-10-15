package com.basic.spendai.sms

import android.content.Context
import com.basic.spendai.ai.AiProvider
import com.basic.spendai.ai.AiTier
import com.basic.spendai.ai.SmsEnricher
import com.basic.spendai.data.AiState
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.data.ExpenseDatabase
import kotlinx.coroutines.sync.Mutex
import java.security.MessageDigest

data class RawSms(val sender: String, val body: String, val timestamp: Long)

/** Turns raw SMS into pending detections. Shared by the inbox scan and the live receiver. */
class SmsImportRepository(context: Context) {
    private val dao = ExpenseDatabase.get(context).detectedTransactionDao()

    /** Returns how many new detections were stored. */
    suspend fun process(messages: List<RawSms>): Int {
        val detections = messages.mapNotNull { sms ->
            val parsed = SmsParser.parse(sms.body) ?: return@mapNotNull null
            val learned = parsed.merchant?.let { dao.getOverride(CategoryGuesser.merchantKey(it)) }
            DetectedTransaction(
                smsKey = smsKey(sms.body),
                sender = sms.sender,
                body = sms.body,
                amount = parsed.amount,
                merchant = parsed.merchant,
                rawMerchant = parsed.rawMerchant,
                suggestedCategory = CategoryGuesser.guess(parsed.merchant, sms.body, learned),
                timestamp = sms.timestamp,
                // A learned category is final; everything else waits for the model (if the phone has one).
                aiState = if (learned != null) AiState.RULES_ONLY else AiState.PENDING_AI,
            )
        }
        if (detections.isEmpty()) return 0
        return dao.insertIgnore(detections).count { it != -1L }
    }

    /**
     * Runs the on-device model over detections it hasn't seen, [BATCH_SIZE] at a time. Must run while
     * the app is in the foreground (Gemini Nano refuses background calls). Stops at the first
     * "model unavailable" so rows are retried on the next app open rather than settled by rules.
     */
    suspend fun enrichPending(provider: AiProvider) {
        if (provider.tier == AiTier.RULES) return
        // Scans run on every resume; don't let two passes enrich the same rows.
        if (!enrichLock.tryLock()) return
        try {
            val enricher = SmsEnricher(provider) { dao.getOverride(it) }
            while (true) {
                val batch = dao.pendingForAi(BATCH_SIZE)
                if (batch.isEmpty()) return
                for (detection in batch) {
                    when (val outcome = enricher.enrich(detection)) {
                        SmsEnricher.Outcome.ModelUnavailable -> return
                        SmsEnricher.Outcome.NotATransaction -> dao.markHidden(detection.id)
                        is SmsEnricher.Outcome.Enriched -> dao.applyEnrichment(
                            id = detection.id,
                            merchant = outcome.merchant,
                            category = outcome.category,
                            aiState = if (outcome.fromAi) AiState.AI_DONE else AiState.RULES_ONLY,
                        )
                    }
                }
            }
        } finally {
            enrichLock.unlock()
        }
    }

    // Bank SMS carry refs/balances, so the body alone identifies a message. The receiver and the
    // inbox report slightly different timestamps for the same SMS, which is why time isn't part of it.
    private fun smsKey(body: String): String {
        val normalized = body.replace(Regex("""\s+"""), " ").trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val BATCH_SIZE = 20
        val enrichLock = Mutex()
    }
}

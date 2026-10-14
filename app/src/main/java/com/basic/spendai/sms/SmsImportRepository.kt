package com.basic.spendai.sms

import android.content.Context
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.data.ExpenseDatabase
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
                suggestedCategory = CategoryGuesser.guess(parsed.merchant, sms.body, learned),
                timestamp = sms.timestamp,
            )
        }
        if (detections.isEmpty()) return 0
        return dao.insertIgnore(detections).count { it != -1L }
    }

    // Bank SMS carry refs/balances, so the body alone identifies a message. The receiver and the
    // inbox report slightly different timestamps for the same SMS, which is why time isn't part of it.
    private fun smsKey(body: String): String {
        val normalized = body.replace(Regex("""\s+"""), " ").trim()
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

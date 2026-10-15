package com.basic.spendai.ai

import com.basic.spendai.data.Category
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.sms.CategoryGuesser
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Lets the on-device model clean up what regex is bad at: the merchant name, the category, and
 * spotting SMS that aren't real spends. Amount and date stay with [com.basic.spendai.sms.SmsParser].
 */
class SmsEnricher(
    private val provider: AiProvider,
    /** The category the user chose for this merchant before, if any. */
    private val learnedCategory: suspend (merchant: String) -> Category?,
) {
    sealed interface Outcome {
        /** The model couldn't run (busy, quota, app in background); leave the row for later. */
        data object ModelUnavailable : Outcome
        data object NotATransaction : Outcome
        data class Enriched(val merchant: String?, val category: Category, val fromAi: Boolean) : Outcome
    }

    suspend fun enrich(detection: DetectedTransaction): Outcome {
        // A user correction beats both rules and model, and saves a model call.
        detection.merchant?.let { name ->
            learnedCategory(CategoryGuesser.merchantKey(name))?.let { return Outcome.Enriched(name, it, fromAi = false) }
        }

        val result = Task.run(provider, Task.Input(detection.body, detection.merchant, detection.suggestedCategory))
        if (!result.replied) return Outcome.ModelUnavailable
        val reply = result.value
        if (!reply.isTransaction) return Outcome.NotATransaction

        // The model may have turned "PAYTM*SWIGGY8291" into "Swiggy", which the user may have corrected before.
        val learned = reply.merchant?.let { learnedCategory(CategoryGuesser.merchantKey(it)) }
        return Outcome.Enriched(reply.merchant, learned ?: reply.category, result.fromAi)
    }

    internal object Task : AiTask<Task.Input, Task.Output>() {
        data class Input(val body: String, val rulesMerchant: String?, val rulesCategory: Category)
        data class Output(val isTransaction: Boolean, val merchant: String?, val category: Category)

        @Serializable
        private data class Reply(
            @SerialName("is_transaction") val isTransaction: Boolean,
            val merchant: String = "",
            val category: String = "",
        )

        override val maxTokens = 64

        override fun prompt(input: Input) = """
            |You read Indian bank SMS. Reply with JSON only, no other text:
            |{"is_transaction": true or false, "merchant": "<shop or person name>", "category": "<one of: $CATEGORY_NAMES>"}
            |is_transaction is false for OTPs, offers, reminders, bills due, failed or upcoming payments.
            |merchant is the business name only, for example "UPI/P2M/PAYTM*SWIGGY8291" becomes "Swiggy". Use "" if unknown.
            """.trimMargin() + "\nSMS: " + input.body.take(MAX_SMS_CHARS)

        override fun parse(reply: String, input: Input): Output? {
            val decoded = AiJson.decode<Reply>(reply) ?: return null
            return Output(
                isTransaction = decoded.isTransaction,
                merchant = validMerchant(decoded.merchant) ?: input.rulesMerchant,
                // An off-list category means the model guessed; the keyword rules are a safer bet.
                category = categoryOrNull(decoded.category) ?: input.rulesCategory,
            )
        }

        override fun fallback(input: Input) = Output(true, input.rulesMerchant, input.rulesCategory)

        private fun validMerchant(name: String): String? {
            val trimmed = name.trim().trim('"', '.')
            return trimmed.takeIf { it.length in 2..30 && it.any(Char::isLetter) }
        }

        private const val MAX_SMS_CHARS = 480
    }
}

package com.basic.spendai.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class DetectionStatus {
    PENDING,
    ACCEPTED,
    DISMISSED,
    /** The AI judged it not a real transaction (promo, reminder…). Kept so the user can restore it. */
    HIDDEN,
}

enum class AiState {
    /** Saved by rules; the on-device model hasn't looked at it yet. */
    PENDING_AI,
    AI_DONE,
    /** Rules (or a learned merchant category) decided; no model call needed. */
    RULES_ONLY,
}

/** A debit parsed from a bank SMS, waiting for the user to add or dismiss it. */
@Entity(
    tableName = "detected_transactions",
    indices = [Index(value = ["smsKey"], unique = true)],
)
data class DetectedTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Hash of the SMS body, so the inbox scan and the live receiver never add the same message twice. */
    val smsKey: String,
    val sender: String,
    val body: String,
    val amount: Double,
    val merchant: String?,
    /** The merchant text as it appeared in the SMS, e.g. "UPI/P2M/PAYTM*SWIGGY8291". */
    val rawMerchant: String? = null,
    val suggestedCategory: Category,
    /** When the SMS arrived; used as the expense date. */
    val timestamp: Long,
    val status: DetectionStatus = DetectionStatus.PENDING,
    @ColumnInfo(defaultValue = "'PENDING_AI'")
    val aiState: AiState = AiState.PENDING_AI,
)

/** A category the user picked for a merchant, reused for that merchant's future SMS. */
@Entity(tableName = "merchant_categories")
data class MerchantCategory(
    @PrimaryKey val merchant: String,
    val category: Category,
)

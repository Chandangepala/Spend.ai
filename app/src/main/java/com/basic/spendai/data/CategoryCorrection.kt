package com.basic.spendai.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class CorrectionSource { AI, RULES }

/**
 * One review decision: what the app suggested for a detected SMS and what the user kept.
 * Rows where [suggested] != [chosen] are corrections; they become few-shot examples for the model
 * and feed the accuracy readout in Settings. Stored only on the device.
 */
@Entity(tableName = "category_corrections")
data class CategoryCorrection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchant: String?,
    val smsSnippet: String,
    val suggested: Category,
    val chosen: Category,
    val source: CorrectionSource,
    val timestamp: Long = System.currentTimeMillis(),
)

/** The name the user gave a raw SMS merchant, e.g. "PAYTM*SWIGGY8291" → "Swiggy". */
@Entity(tableName = "merchant_aliases")
data class MerchantAlias(
    /** Lowercased raw merchant text. */
    @PrimaryKey val rawMerchant: String,
    val displayName: String,
)

/** How many suggestions were reviewed and how many the user kept unchanged. */
data class AccuracyStats(val total: Int, val unchanged: Int) {
    val percent: Int get() = if (total == 0) 0 else (unchanged * 100 + total / 2) / total
}

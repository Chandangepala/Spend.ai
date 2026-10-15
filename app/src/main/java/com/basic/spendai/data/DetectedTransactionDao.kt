package com.basic.spendai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectedTransactionDao {
    @Query("SELECT * FROM detected_transactions WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun observePending(): Flow<List<DetectedTransaction>>

    @Query("SELECT * FROM detected_transactions WHERE status = 'HIDDEN' ORDER BY timestamp DESC")
    fun observeHidden(): Flow<List<DetectedTransaction>>

    /** Pending rows the on-device model hasn't seen yet, newest first. */
    @Query(
        "SELECT * FROM detected_transactions WHERE status = 'PENDING' AND aiState = 'PENDING_AI' " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun pendingForAi(limit: Int): List<DetectedTransaction>

    @Query("UPDATE detected_transactions SET merchant = :merchant, suggestedCategory = :category, aiState = :aiState WHERE id = :id")
    suspend fun applyEnrichment(id: Long, merchant: String?, category: Category, aiState: AiState)

    @Query("UPDATE detected_transactions SET status = 'HIDDEN', aiState = 'AI_DONE' WHERE id = :id")
    suspend fun markHidden(id: Long)

    @Query("UPDATE detected_transactions SET merchant = :merchant WHERE id = :id")
    suspend fun updateMerchant(id: Long, merchant: String)

    /**
     * Applies a rename to every pending row from the same raw SMS merchant, or with the same old name
     * (rows saved before raw merchants were stored have no rawMerchant).
     */
    @Query(
        "UPDATE detected_transactions SET merchant = :merchant WHERE status = 'PENDING' AND " +
            "(rawMerchant = :rawMerchant COLLATE NOCASE OR merchant = :oldName COLLATE NOCASE)"
    )
    suspend fun renamePending(rawMerchant: String?, oldName: String?, merchant: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(transactions: List<DetectedTransaction>): List<Long>

    @Query("UPDATE detected_transactions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: DetectionStatus)

    @Query(
        "UPDATE detected_transactions SET suggestedCategory = :category " +
            "WHERE status = 'PENDING' AND merchant = :merchant COLLATE NOCASE"
    )
    suspend fun updatePendingCategory(merchant: String, category: Category)

    @Query("SELECT category FROM merchant_categories WHERE merchant = :merchant")
    suspend fun getOverride(merchant: String): Category?

    @Upsert
    suspend fun upsertOverride(override: MerchantCategory)
}

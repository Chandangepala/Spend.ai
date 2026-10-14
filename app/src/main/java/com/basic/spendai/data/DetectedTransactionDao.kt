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

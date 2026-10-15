package com.basic.spendai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CorrectionDao {
    @Insert
    suspend fun insert(correction: CategoryCorrection)

    /** Latest decisions where the user changed the suggestion, newest first. */
    @Query(
        "SELECT * FROM category_corrections WHERE suggested != chosen AND merchant IS NOT NULL " +
            "ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun recentCorrections(limit: Int): List<CategoryCorrection>

    @Query(
        "SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN suggested = chosen THEN 1 ELSE 0 END), 0) AS unchanged " +
            "FROM category_corrections WHERE source = :source AND timestamp >= :since"
    )
    fun observeAccuracy(source: CorrectionSource, since: Long): Flow<AccuracyStats>

    @Query("SELECT displayName FROM merchant_aliases WHERE rawMerchant = :rawMerchant")
    suspend fun getAlias(rawMerchant: String): String?

    @Upsert
    suspend fun upsertAlias(alias: MerchantAlias)
}

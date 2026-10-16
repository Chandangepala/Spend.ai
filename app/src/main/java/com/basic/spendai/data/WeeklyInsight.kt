package com.basic.spendai.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * One weekly report. [reportJson] holds the computed findings (an `insights.WeeklyReport`);
 * [summary] is the sentence shown on Home, written later in the foreground (model or template).
 */
@Entity(tableName = "weekly_insights")
data class WeeklyInsight(
    @PrimaryKey val weekFrom: Long,
    val weekUntil: Long,
    val createdAt: Long,
    val reportJson: String,
    val summary: String?,
    val summaryFromAi: Boolean,
)

@Dao
interface WeeklyInsightDao {
    @Query("SELECT * FROM weekly_insights ORDER BY weekFrom DESC LIMIT 1")
    fun observeLatest(): Flow<WeeklyInsight?>

    @Query("SELECT * FROM weekly_insights ORDER BY weekFrom DESC LIMIT 1")
    suspend fun latest(): WeeklyInsight?

    @Upsert
    suspend fun upsert(insight: WeeklyInsight)

    @Query("UPDATE weekly_insights SET summary = :summary, summaryFromAi = :fromAi WHERE weekFrom = :weekFrom")
    suspend fun setSummary(weekFrom: Long, summary: String, fromAi: Boolean)

    /** Keeps the newest [keep] reports. */
    @Query("DELETE FROM weekly_insights WHERE weekFrom NOT IN (SELECT weekFrom FROM weekly_insights ORDER BY weekFrom DESC LIMIT :keep)")
    suspend fun prune(keep: Int)
}

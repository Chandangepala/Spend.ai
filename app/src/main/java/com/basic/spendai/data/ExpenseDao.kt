package com.basic.spendai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<Expense>>

    @Query("SELECT SUM(amount) FROM expenses")
    fun observeTotal(): Flow<Double?>

    /** Expenses that could be the same spend: amount within [minAmount, maxAmount], time within [from, to]. */
    @Query(
        "SELECT * FROM expenses WHERE amount BETWEEN :minAmount AND :maxAmount " +
            "AND timestamp BETWEEN :from AND :to ORDER BY timestamp DESC"
    )
    suspend fun findNear(minAmount: Double, maxAmount: Double, from: Long, to: Long): List<Expense>

    /** Expenses with [from] <= timestamp < [until]. */
    @Query("SELECT * FROM expenses WHERE timestamp >= :from AND timestamp < :until ORDER BY timestamp DESC")
    suspend fun between(from: Long, until: Long): List<Expense>

    @Insert
    suspend fun insert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

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

    @Insert
    suspend fun insert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

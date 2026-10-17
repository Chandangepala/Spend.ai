package com.basic.spendai.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A monthly spending limit for one category. */
@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey val category: Category,
    val monthlyLimit: Double,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Something the user is saving up for. [deadlineEpochDay] is a `LocalDate.toEpochDay()`, or null for no date. */
@Entity(tableName = "savings_goals")
data class SavingsGoal(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val target: Double,
    val saved: Double = 0.0,
    val deadlineEpochDay: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A budget nudge that was already shown, so each level (NEAR, OVER) fires once per category per
 * month. [month] is `YearMonth.toString()`, e.g. "2026-10".
 */
@Entity(tableName = "budget_nudges", primaryKeys = ["category", "month", "level"])
data class BudgetNudge(
    val category: Category,
    val month: String,
    val level: String,
    val shownAt: Long = System.currentTimeMillis(),
)

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY createdAt")
    fun observeBudgets(): Flow<List<Budget>>

    @Upsert
    suspend fun upsertBudget(budget: Budget)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun deleteBudget(category: Category)

    /** Forgets shown nudges, so a budget set again for this category nudges afresh. */
    @Query("DELETE FROM budget_nudges WHERE category = :category")
    suspend fun clearNudges(category: Category)

    @Query("SELECT * FROM savings_goals ORDER BY createdAt")
    fun observeGoals(): Flow<List<SavingsGoal>>

    @Insert
    suspend fun insertGoal(goal: SavingsGoal): Long

    @Update
    suspend fun updateGoal(goal: SavingsGoal)

    @Query("UPDATE savings_goals SET saved = MAX(0, saved + :amount) WHERE id = :id")
    suspend fun addToGoal(id: Long, amount: Double)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoal(id: Long)

    /** Records a nudge; returns false if this one was already shown. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun recordNudge(nudge: BudgetNudge): Long

    /** Keeps only this month's and last month's nudge records. */
    @Query("DELETE FROM budget_nudges WHERE month < :oldestMonth")
    suspend fun pruneNudges(oldestMonth: String)
}

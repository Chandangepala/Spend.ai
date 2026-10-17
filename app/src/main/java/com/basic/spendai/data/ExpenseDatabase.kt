package com.basic.spendai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Expense::class,
        DetectedTransaction::class,
        MerchantCategory::class,
        CategoryCorrection::class,
        MerchantAlias::class,
        WeeklyInsight::class,
        Budget::class,
        SavingsGoal::class,
        BudgetNudge::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun detectedTransactionDao(): DetectedTransactionDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun weeklyInsightDao(): WeeklyInsightDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        @Volatile
        private var instance: ExpenseDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS detected_transactions (
                        id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        smsKey TEXT NOT NULL,
                        sender TEXT NOT NULL,
                        body TEXT NOT NULL,
                        amount REAL NOT NULL,
                        merchant TEXT,
                        suggestedCategory TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_detected_transactions_smsKey " +
                        "ON detected_transactions (smsKey)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS merchant_categories " +
                        "(merchant TEXT NOT NULL PRIMARY KEY, category TEXT NOT NULL)"
                )
            }
        }

        /** Phase 1 AI: raw merchant text, AI processing state, and the HIDDEN status (stored as text). */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE detected_transactions ADD COLUMN rawMerchant TEXT")
                db.execSQL("ALTER TABLE detected_transactions ADD COLUMN aiState TEXT NOT NULL DEFAULT 'PENDING_AI'")
            }
        }

        /** Phase 2 AI: a log of review decisions, and user-given names for raw SMS merchants. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS category_corrections (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        merchant TEXT,
                        smsSnippet TEXT NOT NULL,
                        suggested TEXT NOT NULL,
                        chosen TEXT NOT NULL,
                        source TEXT NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS merchant_aliases " +
                        "(rawMerchant TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL)"
                )
            }
        }

        /** Phase 6: weekly insight reports and their summaries. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS weekly_insights (
                        weekFrom INTEGER NOT NULL PRIMARY KEY,
                        weekUntil INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        reportJson TEXT NOT NULL,
                        summary TEXT,
                        summaryFromAi INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        /** Phase 7: monthly budgets, savings goals, and which budget nudges were already shown. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budgets " +
                        "(category TEXT NOT NULL PRIMARY KEY, monthlyLimit REAL NOT NULL, createdAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS savings_goals (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        target REAL NOT NULL,
                        saved REAL NOT NULL,
                        deadlineEpochDay INTEGER,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budget_nudges (category TEXT NOT NULL, month TEXT NOT NULL, " +
                        "level TEXT NOT NULL, shownAt INTEGER NOT NULL, PRIMARY KEY(category, month, level))"
                )
            }
        }

        fun get(context: Context): ExpenseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "spendai.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build().also { instance = it }
            }
    }
}

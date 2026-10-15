package com.basic.spendai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Expense::class, DetectedTransaction::class, MerchantCategory::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun detectedTransactionDao(): DetectedTransactionDao

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

        fun get(context: Context): ExpenseDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "spendai.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}

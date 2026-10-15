package com.basic.spendai

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.basic.spendai.data.ExpenseDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ExpenseDatabase::class.java,
    )

    @Test
    fun migrate2To3_keepsDetectionsAndQueuesThemForAi() {
        helper.createDatabase(DB, 2).use { db ->
            db.execSQL(
                "INSERT INTO detected_transactions (smsKey, sender, body, amount, merchant, suggestedCategory, timestamp, status) " +
                    "VALUES ('k1', 'AX-AXISBK', 'INR 250 debited', 250.0, 'Swiggy', 'Food', 1000, 'PENDING')"
            )
        }

        helper.runMigrationsAndValidate(DB, 3, true, ExpenseDatabase.MIGRATION_2_3).use { db ->
            db.query("SELECT merchant, rawMerchant, aiState, status FROM detected_transactions").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Swiggy", cursor.getString(0))
                assertNull(cursor.getString(1))
                assertEquals("PENDING_AI", cursor.getString(2))
                assertEquals("PENDING", cursor.getString(3))
            }
        }
    }

    @Test
    fun migrate3To4_addsEmptyLearningTables() {
        helper.createDatabase(DB, 3).use { db ->
            db.execSQL(
                "INSERT INTO merchant_categories (merchant, category) VALUES ('swiggy', 'Food')"
            )
        }

        helper.runMigrationsAndValidate(DB, 4, true, ExpenseDatabase.MIGRATION_3_4).use { db ->
            db.query("SELECT COUNT(*) FROM category_corrections").use { assertEquals(0, it.apply { moveToFirst() }.getInt(0)) }
            db.query("SELECT COUNT(*) FROM merchant_aliases").use { assertEquals(0, it.apply { moveToFirst() }.getInt(0)) }
            db.query("SELECT category FROM merchant_categories WHERE merchant = 'swiggy'").use {
                it.moveToFirst()
                assertEquals("Food", it.getString(0))
            }
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}

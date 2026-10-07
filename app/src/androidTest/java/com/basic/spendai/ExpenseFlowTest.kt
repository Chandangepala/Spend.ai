package com.basic.spendai

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.ExpenseDatabase
import com.basic.spendai.data.SettingsRepository
import com.basic.spendai.util.formatCurrency
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExpenseFlowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ExpenseDatabase.get(context).clearAllTables()
        runBlocking { SettingsRepository(context).setCurrency(AppCurrency.USD) }
    }

    private fun waitUntilOne(matcher: SemanticsMatcher, timeoutMillis: Long = 5_000) = composeRule.waitUntil(timeoutMillis) {
        composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().size == 1
    }

    // Amounts can appear in both the Total card and the category breakdown on wide screens.
    private fun waitUntilAny(matcher: SemanticsMatcher) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun waitUntilGone(matcher: SemanticsMatcher) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }

    private fun addExpense(title: String, amount: String, category: String) {
        // Let the previous sheet finish animating out before opening a new one.
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Add expense", useUnmergedTree = true).performClick()
        waitUntilOne(hasText("Save expense"), timeoutMillis = 10_000)
        composeRule.onNode(hasSetTextAction() and hasText("What did you spend on?")).performTextInput(title)
        composeRule.onNode(hasSetTextAction() and hasText("Amount")).performTextInput(amount)
        composeRule.onNode(hasClickAction() and hasText(category)).performClick()
        composeRule.onNodeWithText("Save expense", useUnmergedTree = true).performClick()
        waitUntilGone(hasText("Save expense"))
        waitUntilOne(hasText(title))
    }

    @Test
    fun addAndDeleteExpenses_updatesListAndTotal() {
        waitUntilOne(hasText("No expenses yet"))

        addExpense("Lunch", "12.50", "Food")
        waitUntilAny(hasText(formatCurrency(12.5, AppCurrency.USD)))

        addExpense("Metro", "3", "Transport")
        waitUntilAny(hasText(formatCurrency(15.5, AppCurrency.USD)))
        composeRule.onNodeWithText("2 transactions", useUnmergedTree = true).assertExists()

        composeRule.onNodeWithText("Lunch", useUnmergedTree = true).performTouchInput { swipeLeft() }
        waitUntilGone(hasText("Lunch"))
        waitUntilAny(hasText(formatCurrency(3.0, AppCurrency.USD)))
        composeRule.onNodeWithText("1 transaction", useUnmergedTree = true).assertExists()
    }

    @Test
    fun saveWithoutAmount_showsValidationError() {
        composeRule.onNodeWithText("Add expense", useUnmergedTree = true).performClick()
        waitUntilOne(hasText("Save expense"))
        composeRule.onNode(hasSetTextAction() and hasText("What did you spend on?")).performTextInput("Coffee")
        composeRule.onNodeWithText("Save expense", useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Enter an amount greater than 0", useUnmergedTree = true).assertExists()
    }

    @Test
    fun changingCurrencyInSettings_updatesShownAmounts() {
        addExpense("Lunch", "12.50", "Food")
        waitUntilAny(hasText(formatCurrency(12.5, AppCurrency.USD)))

        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitUntilOne(hasText("Euro"))
        composeRule.onNode(hasClickAction() and hasText("Euro")).performClick()
        composeRule.onNodeWithContentDescription("Back").performClick()

        waitUntilAny(hasText(formatCurrency(12.5, AppCurrency.EUR)))
        waitUntilGone(hasText(formatCurrency(12.5, AppCurrency.USD)))
    }
}

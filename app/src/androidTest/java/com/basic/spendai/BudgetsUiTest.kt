package com.basic.spendai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basic.spendai.ai.AiResult
import com.basic.spendai.ai.CoachWriter
import com.basic.spendai.budgets.BudgetProgress
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.SavingsGoal
import com.basic.spendai.ui.budgets.BudgetsCard
import com.basic.spendai.ui.budgets.GoalsCard
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Budget rows and goal rows with fixed numbers. No database involved. */
@RunWith(AndroidJUnit4::class)
class BudgetsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    /** The coach as if no model were available: callers fall back to the template. */
    private val noAi: suspend (CoachWriter.Topic) -> AiResult<String> = { AiResult("", fromAi = false) }

    @Test
    fun budgetRowsShowStatusInWords() {
        var edited: Category? = null
        composeRule.setContent {
            CompositionLocalProvider(LocalCurrency provides AppCurrency.INR) {
                SpendaiTheme {
                    BudgetsCard(
                        budgets = listOf(
                            BudgetProgress(Category.Food, 6000.0, 5100.0, 10),
                            BudgetProgress(Category.Shopping, 2000.0, 2300.0, 1),
                            BudgetProgress(Category.Transport, 3000.0, 600.0, 10),
                        ),
                        onAdd = {},
                        onEdit = { edited = it.category },
                        canAdd = true,
                    )
                }
            }
        }
        composeRule.onNodeWithText("85% used · ₹900.00 left · 10 days to go").assertExists()
        composeRule.onNodeWithText("Over by ₹300.00 · 1 day to go").assertExists()
        composeRule.onNodeWithText("20% used · ₹2,400.00 left · 10 days to go").assertExists()
        composeRule.onNodeWithText("Shopping").performClick()
        assertEquals(Category.Shopping, edited)
    }

    @Test
    fun goalRowShowsPlanAndAddMoney() {
        val deadline = LocalDate.of(2027, 3, 31)
        var adding: SavingsGoal? = null
        val goal = SavingsGoal(id = 1, name = "Goa trip", target = 30_000.0, saved = 12_000.0, deadlineEpochDay = deadline.toEpochDay())
        composeRule.setContent {
            CompositionLocalProvider(LocalCurrency provides AppCurrency.INR) {
                SpendaiTheme {
                    GoalsCard(listOf(goal), onAdd = {}, onEdit = {}, onAddMoney = { adding = it }, onCoach = noAi, today = LocalDate.of(2026, 10, 21))
                }
            }
        }
        composeRule.onNodeWithText("₹12,000.00 of ₹30,000.00").assertExists()
        composeRule.onNodeWithText("40%").assertExists()
        composeRule.onNodeWithText("Put aside ₹3,000.00 a month to reach Goa trip by Mar 31, 2027.").assertExists()
        composeRule.onNodeWithText("Add money").performClick()
        assertEquals(goal, adding)
    }
}

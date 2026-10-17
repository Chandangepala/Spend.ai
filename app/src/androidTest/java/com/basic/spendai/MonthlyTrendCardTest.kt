package com.basic.spendai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.insights.MonthTotal
import com.basic.spendai.ui.insights.MonthlyTrendCard
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The Insights trend chart: tap-to-read values and the list view. No database involved. */
@RunWith(AndroidJUnit4::class)
class MonthlyTrendCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val months = listOf(
        MonthTotal(YearMonth.of(2026, 5), 12_400.0, 30, false),
        MonthTotal(YearMonth.of(2026, 6), 9_800.0, 25, false),
        MonthTotal(YearMonth.of(2026, 7), 0.0, 0, false),
        MonthTotal(YearMonth.of(2026, 8), 15_250.5, 41, false),
        MonthTotal(YearMonth.of(2026, 9), 11_000.0, 28, false),
        MonthTotal(YearMonth.of(2026, 10), 8_919.22, 15, true),
    )
    private fun inr(v: Double) = formatCurrency(v, AppCurrency.INR)
    private fun name(m: Month) = m.getDisplayName(TextStyle.FULL, Locale.getDefault())

    @Before
    fun show() {
        composeRule.setContent {
            CompositionLocalProvider(LocalCurrency provides AppCurrency.INR) {
                SpendaiTheme { MonthlyTrendCard(months) }
            }
        }
    }

    @Test
    fun currentMonthIsSelectedAndMarkedSoFar() {
        composeRule.onNodeWithText("${name(Month.OCTOBER)} 2026 · so far").assertExists()
        composeRule.onNodeWithText(inr(8_919.22)).assertExists()
    }

    @Test
    fun tappingAMonthShowsItsAmount() {
        composeRule.onNodeWithContentDescription("${name(Month.AUGUST)} 2026: ${inr(15_250.5)}").performClick()
        composeRule.onNodeWithText("${name(Month.AUGUST)} 2026").assertExists()
        composeRule.onNodeWithText(inr(15_250.5)).assertExists()
    }

    @Test
    fun listViewShowsEveryMonth() {
        composeRule.onNodeWithText("View as list").performClick()
        months.forEach { composeRule.onAllNodesWithText(inr(it.total)).assertCountEquals(1) }
        composeRule.onNodeWithText("View as chart").assertExists()
    }
}

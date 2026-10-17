package com.basic.spendai

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.ui.home.TotalSpentCard
import com.basic.spendai.ui.motion.LocalReducedMotion
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Decorative motion must land on exact values, and disappear entirely with "Remove animations". */
@RunWith(AndroidJUnit4::class)
class MotionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun inr(v: Double) = formatCurrency(v, AppCurrency.INR)

    private fun show(total: () -> Double, reduced: Boolean) {
        composeRule.setContent {
            CompositionLocalProvider(LocalCurrency provides AppCurrency.INR, LocalReducedMotion provides reduced) {
                SpendaiTheme { TotalSpentCard(total = total(), expenses = emptyList()) }
            }
        }
    }

    @Test
    fun countUpEndsOnTheExactAmountEvenWhenLarge() {
        show({ 1_234_567.89 }, reduced = false)
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithText(inr(1_234_567.89)).assertExists()
    }

    @Test
    fun countUpIsInProgressPartWay() {
        composeRule.mainClock.autoAdvance = false
        show({ 8_919.22 }, reduced = false)
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithText(inr(8_919.22)).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithText(inr(8_919.22)).assertExists()
    }

    @Test
    fun reducedMotionShowsTheFinalValueOnTheFirstFrame() {
        composeRule.mainClock.autoAdvance = false
        show({ 8_919.22 }, reduced = true)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText(inr(8_919.22)).assertExists()
    }

    @Test
    fun changingTheTotalCountsToTheNewValue() {
        var total by mutableDoubleStateOf(1_000.0)
        show({ total }, reduced = false)
        composeRule.mainClock.advanceTimeBy(2_000)
        total = 2_550.5
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithText(inr(2_550.5)).assertExists()
    }
}

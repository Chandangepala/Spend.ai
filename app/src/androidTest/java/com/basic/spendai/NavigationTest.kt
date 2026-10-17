package com.basic.spendai

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Tabs, Settings on top, and back behaviour. Only reads app data, so it's safe on a real device. */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    private fun gone(text: String) = composeRule.waitUntil(5_000) {
        composeRule.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
    }

    private fun tab(label: String) = composeRule.onNodeWithText(label, useUnmergedTree = true).performClick()

    @Test
    fun tabsSwitchAndBackReturnsHome() {
        waitFor("Spent this month")
        tab("Activity")
        waitFor("Expenses")
        tab("Insights")
        waitFor("This month")
        tab("Ask")
        waitFor("Ask your spends")
        pressBack()
        waitFor("Spent this month")
    }

    @Test
    fun settingsOpensOnTopWithoutTheBarAndBackRestoresIt() {
        waitFor("Spent this month")
        tab("Activity")
        waitFor("Expenses")
        composeRule.onNodeWithContentDescription("Settings").performClick()
        waitFor("Currency")
        gone("Insights") // bottom bar hidden while Settings is open
        composeRule.onNodeWithContentDescription("Back").performClick()
        waitFor("Expenses") // back on the tab we came from
        waitFor("Insights")
    }
}

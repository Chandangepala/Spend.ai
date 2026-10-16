package com.basic.spendai

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.ExpenseUiState
import com.basic.spendai.ui.home.HomeContent
import com.basic.spendai.ui.home.HomeLayout
import com.basic.spendai.ui.home.HomeLayoutInfo
import com.basic.spendai.ui.theme.SpendaiTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Swipe-to-delete must never delete on its own: a swipe only reveals "Delete", which then has to be
 * tapped. Renders the Home list only, so it doesn't touch the app's database.
 */
@RunWith(AndroidJUnit4::class)
class SwipeDeleteTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val deleted = mutableListOf<Expense>()
    private val edited = mutableListOf<Expense>()
    private val lunch = Expense(1, "Lunch", 250.0, Category.Food, 1_000)
    private val metro = Expense(2, "Metro", 40.0, Category.Transport, 900)

    @Before
    fun show() {
        composeRule.setContent {
            SpendaiTheme {
                HomeContent(
                    state = ExpenseUiState(expenses = listOf(lunch, metro), total = 290.0),
                    layoutInfo = HomeLayoutInfo(HomeLayout.SinglePane),
                    onDelete = { deleted += it },
                    onEdit = { edited += it },
                )
            }
        }
    }

    private fun row(title: String) = composeRule.onNodeWithText(title, useUnmergedTree = true)
    private fun deleteButtons() = composeRule.onAllNodesWithText("Delete", useUnmergedTree = true)

    @Test
    fun shortDragDoesNothing() {
        // About 15% of the row: well under half of the 96dp action.
        row("Lunch").performTouchInput { swipeLeft(startX = centerX, endX = centerX - width * 0.06f) }
        composeRule.waitForIdle()
        deleteButtons().assertCountEquals(0)
        assertEquals(emptyList<Expense>(), deleted)
    }

    @Test
    fun swipingRightNeverDeletes() {
        row("Lunch").performTouchInput { swipeRight() }
        composeRule.waitForIdle()
        deleteButtons().assertCountEquals(0)
        assertEquals(emptyList<Expense>(), deleted)
    }

    @Test
    fun fullSwipeOnlyRevealsDelete() {
        row("Lunch").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        deleteButtons().assertCountEquals(1)
        assertEquals(emptyList<Expense>(), deleted)
    }

    @Test
    fun tappingDeleteDeletesOnce() {
        row("Lunch").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Delete", useUnmergedTree = true).performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(lunch), deleted)
    }

    @Test
    fun tappingAnOpenRowClosesItInsteadOfEditing() {
        row("Lunch").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        row("Lunch").performClick()
        composeRule.waitForIdle()
        deleteButtons().assertCountEquals(0)
        assertEquals(emptyList<Expense>(), edited)
        // Closed again, so the next tap edits.
        row("Lunch").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(lunch), edited)
    }

    @Test
    fun openingAnotherRowClosesTheFirst() {
        row("Lunch").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        row("Metro").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()
        deleteButtons().assertCountEquals(1)
        assertEquals(emptyList<Expense>(), deleted)
    }
}

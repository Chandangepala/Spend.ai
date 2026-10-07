package com.basic.spendai

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basic.spendai.data.Category
import com.basic.spendai.data.Expense
import com.basic.spendai.ui.ExpenseUiState
import com.basic.spendai.ui.home.HomeContent
import com.basic.spendai.ui.home.HomeLayout
import com.basic.spendai.ui.home.HomeLayoutInfo
import com.basic.spendai.ui.theme.SpendaiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val state = ExpenseUiState(
        expenses = listOf(
            Expense(id = 1, title = "Lunch", amount = 12.5, category = Category.Food),
            Expense(id = 2, title = "Metro", amount = 3.0, category = Category.Transport),
        ),
        total = 15.5,
    )

    private fun show(layoutInfo: HomeLayoutInfo) {
        composeRule.setContent {
            SpendaiTheme { HomeContent(state, layoutInfo, onDelete = {}) }
        }
    }

    private fun node(text: String): SemanticsNode =
        composeRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()

    @Test
    fun singlePane_showsCardAndListWithoutBreakdown() {
        show(HomeLayoutInfo(HomeLayout.SinglePane))
        node("Total Spent")
        node("Lunch")
        composeRule.onNodeWithText("By category", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun twoPane_placesSummaryBesideList() {
        show(HomeLayoutInfo(HomeLayout.TwoPane))
        val summary = node("By category").boundsInWindow
        val list = node("Lunch").boundsInWindow
        assertTrue("summary $summary should be left of list $list", summary.right <= list.left)
    }

    @Test
    fun twoPane_withVerticalHinge_keepsContentOffTheFold() {
        val hinge = HingeInfo(
            bounds = Rect(left = 500f, top = 0f, right = 540f, bottom = 4000f),
            isFlat = false,
            isVertical = true,
            isSeparating = true,
            isOccluding = true,
        )
        show(HomeLayoutInfo(HomeLayout.TwoPane, hinge))
        val summary = node("By category").boundsInWindow
        val list = node("Lunch").boundsInWindow
        assertTrue("summary $summary should end before hinge", summary.right <= hinge.bounds.left)
        assertTrue("list $list should start after hinge", list.left >= hinge.bounds.right)
    }

    @Test
    fun tabletop_withHorizontalHinge_putsSummaryAboveFoldAndListBelow() {
        val hinge = HingeInfo(
            bounds = Rect(left = 0f, top = 900f, right = 4000f, bottom = 940f),
            isFlat = false,
            isVertical = false,
            isSeparating = true,
            isOccluding = true,
        )
        show(HomeLayoutInfo(HomeLayout.Tabletop, hinge))
        val summary = node("Total Spent").boundsInWindow
        val list = node("Lunch").boundsInWindow
        assertTrue("summary $summary should be above hinge", summary.bottom <= hinge.bounds.top)
        assertTrue("list $list should be below hinge", list.top >= hinge.bounds.bottom)
    }
}

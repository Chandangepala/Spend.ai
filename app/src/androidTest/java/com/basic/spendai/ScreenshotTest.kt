package com.basic.spendai

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.data.Category
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.data.Expense
import com.basic.spendai.insights.Finding
import com.basic.spendai.insights.MonthTotal
import com.basic.spendai.insights.Subscription
import com.basic.spendai.ui.ExpenseUiState
import com.basic.spendai.ui.components.EmptyState
import com.basic.spendai.ui.detected.DetectedTransactionsScreen
import com.basic.spendai.ui.home.CategoryBreakdown
import com.basic.spendai.ui.home.HomeContent
import com.basic.spendai.ui.home.HomeLayout
import com.basic.spendai.ui.home.HomeLayoutInfo
import com.basic.spendai.ui.insights.MonthlyTrendCard
import com.basic.spendai.ui.insights.RepeatingPaymentsCard
import com.basic.spendai.ui.motion.LocalReducedMotion
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.YearMonth

/**
 * Renders screens in light/dark, large font and foldable width and saves PNGs in the app's own
 * files dir (files/screens) for review. Changes no device setting and no app data.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val expenses = listOf(
        Expense(1, "Policybazaar", 2597.5, Category.Finance, now - 1 * 3_600_000),
        Expense(2, "Swiggy", 640.0, Category.Food, now - 5 * 3_600_000),
        Expense(3, "Blinkit", 1210.0, Category.Groceries, now - 26 * 3_600_000),
        Expense(4, "Airtel recharge", 299.0, Category.Bills, now - 30 * 3_600_000),
        Expense(5, "Uber", 412.0, Category.Transport, now - 50 * 3_600_000),
        Expense(6, "Netflix", 649.0, Category.Entertainment, now - 70 * 3_600_000),
    )
    private val state = ExpenseUiState(expenses = expenses, total = expenses.sumOf { it.amount })

    /** [scale] < 1 renders wide layouts (e.g. an 840dp foldable) small enough to fit the phone's screen. */
    private fun capture(name: String, dark: Boolean, width: Dp, height: Dp, fontScale: Float = 1f, scale: Float = 1f, content: @Composable () -> Unit) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalCurrency provides AppCurrency.INR,
                LocalReducedMotion provides true, // final state, no mid-animation frames
                LocalDensity provides Density(density.density * scale, fontScale),
            ) {
                SpendaiTheme(darkTheme = dark) {
                    Box(Modifier.requiredSize(width, height).background(MaterialTheme.colorScheme.background).testTag("shot")) { content() }
                }
            }
        }
        val bitmap = composeRule.onNodeWithTag("shot").captureToImage().asAndroidBitmap()
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "screens").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun homeLight() = capture("home_light", dark = false, 411.dp, 800.dp) { HomeContent(state, HomeLayoutInfo(HomeLayout.SinglePane), onDelete = {}) }
    @Test fun homeDark() = capture("home_dark", dark = true, 411.dp, 800.dp) { HomeContent(state, HomeLayoutInfo(HomeLayout.SinglePane), onDelete = {}) }
    @Test fun homeLargeFont() = capture("home_font130", dark = false, 411.dp, 800.dp, fontScale = 1.3f) { HomeContent(state, HomeLayoutInfo(HomeLayout.SinglePane), onDelete = {}) }
    @Test fun homeFoldableDark() = capture("home_twopane_dark", dark = true, 840.dp, 600.dp, scale = 0.45f) { HomeContent(state, HomeLayoutInfo(HomeLayout.TwoPane), onDelete = {}) }

    @Test
    fun insightsDark() = capture("insights_dark", dark = true, 411.dp, 1100.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MonthlyTrendCard((5..10).map { MonthTotal(YearMonth.of(2026, it), listOf(9800.0, 12400.0, 0.0, 15250.5, 11000.0, 8919.22)[it - 5], 10, it == 10) })
            CategoryBreakdown(expenses)
            RepeatingPaymentsCard(Finding.Subscriptions(listOf(Subscription("Netflix", 649.0), Subscription("Cult.fit", 1550.0)), 2199.0))
        }
    }

    @Test
    fun detectedDark() = capture("detected_dark", dark = true, 411.dp, 520.dp) {
        DetectedTransactionsScreen(
            detections = listOf(
                DetectedTransaction(1, "k", "AD-DCBANK-T", "INR 7282.00 debited DCB a/c 2820; to CRED; UPI 664630959482.", 7282.0, "Cred", suggestedCategory = Category.Finance, timestamp = now),
            ),
            onAccept = { _, _ -> }, onDismiss = {}, onAcceptAll = {}, onBack = {},
        )
    }

    @Test
    fun emptyDark() = capture("empty_dark", dark = true, 411.dp, 420.dp) {
        EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "No expenses yet", "Add one with the button below, type a quick note, or scan a receipt.", actionLabel = "Add expense")
    }
}

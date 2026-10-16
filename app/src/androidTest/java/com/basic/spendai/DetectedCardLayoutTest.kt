package com.basic.spendai

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.basic.spendai.data.Category
import com.basic.spendai.data.DetectedTransaction
import com.basic.spendai.ui.detected.DetectedTransactionsScreen
import com.basic.spendai.ui.theme.SpendaiTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the Detected card's action row: a long category label must never squeeze
 * "Add"/"Dismiss" (they used to wrap letter by letter and grow tall). Renders only the screen,
 * so it doesn't touch the app's database.
 */
@RunWith(AndroidJUnit4::class)
class DetectedCardLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun detection(category: Category) = DetectedTransaction(
        id = 1,
        smsKey = "k",
        sender = "JD-DCBANK-S",
        body = "INR 7282.00 debited DCB a/c 2820; to CRED; UPI 664630959482.",
        amount = 7282.0,
        merchant = "Cred",
        suggestedCategory = category,
        timestamp = 0,
    )

    @Test
    fun buttonsKeepTheirShapeForEveryCategoryWidthAndFontSize() {
        var category by mutableStateOf(Category.Food)
        var width by mutableStateOf(411.dp)
        var fontScale by mutableStateOf(1f)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SpendaiTheme {
                    Box(Modifier.width(width)) {
                        DetectedTransactionsScreen(
                            detections = listOf(detection(category)),
                            onAccept = { _, _ -> },
                            onDismiss = {},
                            onAcceptAll = {},
                            onBack = {},
                        )
                    }
                }
            }
        }

        // 320dp = small phone, 411dp = Pixel 6a, 600dp = foldable/tablet column.
        for (w in listOf(320.dp, 411.dp, 600.dp)) for (scale in listOf(1f, 1.3f)) for (c in Category.entries) {
            width = w
            fontScale = scale
            category = c
            composeRule.waitForIdle()
            for (label in listOf("Add", "Dismiss")) {
                val bounds = composeRule.onNodeWithText(label, useUnmergedTree = true).getBoundsInRoot()
                val where = "$label with ${c.label} at $w, font x$scale"
                // One line of label text: well under two lines' height, and wider than a single letter.
                assertTrue("$where: text is ${bounds.height} tall", bounds.height < (30.dp * scale))
                assertTrue("$where: text is only ${bounds.width} wide", bounds.width > (18.dp * scale))
                assertTrue("$where: overflows the screen (${bounds.right} > $w)", bounds.right <= w)
            }
        }
    }

    private operator fun Dp.times(f: Float): Dp = (value * f).dp
}

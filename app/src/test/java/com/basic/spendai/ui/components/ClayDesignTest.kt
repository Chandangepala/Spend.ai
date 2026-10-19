package com.basic.spendai.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import com.basic.spendai.data.Category
import com.basic.spendai.ui.home.smallDecimals
import com.basic.spendai.ui.theme.ClayGround
import com.basic.spendai.ui.theme.ClayInk
import com.basic.spendai.ui.theme.ClayMuted
import com.basic.spendai.ui.theme.EmeraldDeep
import com.basic.spendai.ui.theme.HeroTop
import com.basic.spendai.ui.theme.Mint100
import com.basic.spendai.ui.theme.Mint200
import com.basic.spendai.ui.theme.NavGround
import com.basic.spendai.ui.theme.NavMuted
import com.basic.spendai.ui.theme.NavPill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ClayDesignTest {

    // --- Title emoji ---

    @Test
    fun knownSpendsGetAnEmoji() {
        assertEquals("🔌", titleDecor("Electricity", Category.Bills).emoji)
        assertEquals("🍔", titleDecor("Swiggy Food", Category.Food).emoji)
        assertEquals("🚕", titleDecor("Uber trip", Category.Transport).emoji)
        // SMS merchants with digits and symbols glued on.
        assertEquals("🛒", titleDecor("Paytm*blinkit4412", Category.Groceries).emoji)
    }

    @Test
    fun titlesWithTheirOwnEmojiDontGetASecond() {
        assertNull(titleDecor("Electricity 🔌", Category.Bills).emoji)
        assertNull(titleDecor("Coffee ☕", Category.Food).emoji)
        // The tile icon still follows the words.
        assertEquals(Icons.Rounded.Cloud, titleDecor("Google Cloud ☁️", Category.Other).icon)
    }

    @Test
    fun onlyWholeWordsMatch() {
        // "rent" inside "parent" or "current" isn't rent; "tea" inside "steak" isn't tea.
        assertNull(titleDecor("Parent teacher meeting", Category.Education).emoji)
        assertNull(titleDecor("Steak dinner", Category.Food).emoji)
        assertNull(titleDecor("Brajesh", Category.Other).emoji)
    }

    @Test
    fun theTileKeepsTheCategoryIconExceptForOther() {
        assertEquals(Category.Bills.icon, titleDecor("Electricity", Category.Bills).icon)
        // "Other" says nothing, so the matching icon is shown instead.
        assertEquals(Icons.Rounded.Cloud, titleDecor("Google Cloud", Category.Other).icon)
        assertEquals(Category.Other.icon, titleDecor("Brajesh", Category.Other).icon)
    }

    // --- The hero amount ---

    @Test
    fun onlyTheDecimalsAreRestyled() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            val styled = smallDecimals("₹25,778.78", SpanStyle(color = Color.Red))
            assertEquals("₹25,778.78", styled.text)
            assertEquals(listOf(7 to 10), styled.spanStyles.map { it.start to it.end })
            // No minor unit (yen): nothing to restyle.
            assertTrue(smallDecimals("¥1,250", SpanStyle()).spanStyles.isEmpty())
        } finally {
            Locale.setDefault(previous)
        }
    }

    // --- Contrast (WCAG AA: 4.5:1 for text) ---

    private fun contrast(text: Color, background: Color): Double {
        val a = text.compositeOver(background).luminance() + 0.05
        val b = background.luminance() + 0.05
        return maxOf(a, b) / minOf(a, b)
    }

    @Test
    fun textColoursAreReadable() {
        assertTrue(contrast(ClayInk, Color.White) >= 4.5)
        assertTrue(contrast(ClayMuted, Color.White) >= 4.5)
        assertTrue(contrast(ClayMuted, ClayGround) >= 4.5)
        assertTrue(contrast(NavMuted, NavGround) >= 4.5)
        assertTrue(contrast(EmeraldDeep, NavPill) >= 4.5)
        // On the hero gradient's lightest stop.
        assertTrue(contrast(Color.White, HeroTop) >= 4.5)
        assertTrue(contrast(Mint100, HeroTop) >= 4.5)
        assertTrue(contrast(Mint200, HeroTop) >= 4.5)
    }
}

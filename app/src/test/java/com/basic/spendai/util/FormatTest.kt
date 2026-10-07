package com.basic.spendai.util

import com.basic.spendai.data.AppCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatTest {

    @Test
    fun usesTheChosenCurrencySymbol() {
        assertTrue(formatCurrency(12.5, AppCurrency.EUR).contains("€"))
        assertTrue(formatCurrency(12.5, AppCurrency.INR).contains("₹"))
        assertTrue(formatCurrency(12.5, AppCurrency.GBP).contains("£"))
    }

    @Test
    fun yenHasNoDecimalsWhileDollarHasTwo() {
        assertTrue(formatCurrency(1250.0, AppCurrency.JPY).endsWith("1,250"))
        assertTrue(formatCurrency(1250.0, AppCurrency.USD).endsWith("1,250.00"))
    }

    @Test
    fun unknownStoredCodeFallsBackToNull() {
        assertEquals(null, AppCurrency.fromCode("XYZ"))
        assertEquals(AppCurrency.INR, AppCurrency.fromCode("INR"))
    }
}

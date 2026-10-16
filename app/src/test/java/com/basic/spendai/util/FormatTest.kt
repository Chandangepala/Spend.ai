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

    @Test
    fun changingTheDateKeepsTheTimeOfDay() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        val original = java.time.LocalDateTime.of(2026, 10, 14, 19, 29).atZone(zone).toInstant().toEpochMilli()
        // The picker hands back UTC midnight of the chosen day.
        val picked = java.time.LocalDate.of(2026, 10, 12).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        val updated = withPickedDate(original, picked, zone)
        assertEquals(java.time.LocalDateTime.of(2026, 10, 12, 19, 29), java.time.Instant.ofEpochMilli(updated).atZone(zone).toLocalDateTime())
    }

    @Test
    fun pickerDateIsTheLocalDayAtUtcMidnight() {
        val zone = java.time.ZoneId.of("Asia/Kolkata")
        // 00:30 IST on Oct 17 is still Oct 16 in UTC; the picker must show Oct 17.
        val earlyMorning = java.time.LocalDateTime.of(2026, 10, 17, 0, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(
            java.time.LocalDate.of(2026, 10, 17).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            toPickerDate(earlyMorning, zone),
        )
    }
}

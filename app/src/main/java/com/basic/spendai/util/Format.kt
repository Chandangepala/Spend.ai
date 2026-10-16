package com.basic.spendai.util

import androidx.compose.runtime.compositionLocalOf
import com.basic.spendai.data.AppCurrency
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale

/** The currency chosen in Settings, provided at the top of the UI tree. */
val LocalCurrency = compositionLocalOf { AppCurrency.default() }

private val currencyFormats = mutableMapOf<AppCurrency, NumberFormat>()
private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
private val dayFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

fun formatCurrency(amount: Double, currency: AppCurrency): String =
    currencyFormats.getOrPut(currency) {
        NumberFormat.getCurrencyInstance().apply {
            this.currency = currency.javaCurrency
            // e.g. Yen has no minor unit, so show ¥1,250 rather than ¥1,250.00
            minimumFractionDigits = currency.javaCurrency.defaultFractionDigits
            maximumFractionDigits = currency.javaCurrency.defaultFractionDigits
        }
    }.format(amount)

fun formatDate(timestamp: Long): String = dateFormat.format(Date(timestamp))

/**
 * The Material date picker works in UTC midnights. These convert between an expense's timestamp
 * (local time) and the picker's value, keeping the expense's time of day when only the date changes.
 */
fun toPickerDate(timestamp: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
    Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun withPickedDate(timestamp: Long, pickerDate: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val time = Instant.ofEpochMilli(timestamp).atZone(zone).toLocalTime()
    val date = Instant.ofEpochMilli(pickerDate).atZone(ZoneOffset.UTC).toLocalDate()
    return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
}

/** "Oct 14, 2026" for the edit sheet's date field. */
fun formatDay(timestamp: Long): String = dayFormat.format(Date(timestamp))

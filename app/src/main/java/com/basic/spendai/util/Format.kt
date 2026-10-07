package com.basic.spendai.util

import androidx.compose.runtime.compositionLocalOf
import com.basic.spendai.data.AppCurrency
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The currency chosen in Settings, provided at the top of the UI tree. */
val LocalCurrency = compositionLocalOf { AppCurrency.default() }

private val currencyFormats = mutableMapOf<AppCurrency, NumberFormat>()
private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

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

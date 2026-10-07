package com.basic.spendai.data

import java.util.Currency
import java.util.Locale

/** Currencies the user can pick in Settings. Amounts are displayed in it; nothing is converted. */
enum class AppCurrency(val code: String, val displayName: String) {
    INR("INR", "Indian Rupee"),
    USD("USD", "US Dollar"),
    EUR("EUR", "Euro"),
    JPY("JPY", "Japanese Yen"),
    GBP("GBP", "British Pound"),
    CNY("CNY", "Chinese Yuan"),
    AUD("AUD", "Australian Dollar"),
    CAD("CAD", "Canadian Dollar"),
    AED("AED", "UAE Dirham"),
    SGD("SGD", "Singapore Dollar");

    val javaCurrency: Currency get() = Currency.getInstance(code)

    val symbol: String get() = javaCurrency.getSymbol(Locale.getDefault())

    companion object {
        fun fromCode(code: String?): AppCurrency? = entries.firstOrNull { it.code == code }

        /** The device locale's currency when we support it, otherwise US Dollar. */
        fun default(): AppCurrency =
            runCatching { fromCode(Currency.getInstance(Locale.getDefault()).currencyCode) }.getOrNull() ?: USD
    }
}

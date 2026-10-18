package com.basic.spendai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val currency: Flow<AppCurrency> = dataStore.data.map { prefs ->
        AppCurrency.fromCode(prefs[CURRENCY_KEY]) ?: AppCurrency.default()
    }

    val smsImportEnabled: Flow<Boolean> = dataStore.data.map { it[SMS_IMPORT_KEY] ?: false }

    /** Master switch for on-device AI; when off every feature uses its rules. */
    val aiEnabled: Flow<Boolean> = dataStore.data.map { it[AI_ENABLED_KEY] ?: true }

    /** Weekly summary notification; off until the user turns it on (and grants permission). */
    val insightsNotify: Flow<Boolean> = dataStore.data.map { it[INSIGHTS_NOTIFY_KEY] ?: false }

    /** Inbox messages up to this time have already been scanned; null before the first scan. */
    val lastSmsScanAt: Flow<Long?> = dataStore.data.map { it[LAST_SMS_SCAN_KEY] }

    suspend fun setCurrency(currency: AppCurrency) {
        dataStore.edit { it[CURRENCY_KEY] = currency.code }
    }

    suspend fun setSmsImportEnabled(enabled: Boolean) {
        dataStore.edit { it[SMS_IMPORT_KEY] = enabled }
    }

    suspend fun setAiEnabled(enabled: Boolean) {
        dataStore.edit { it[AI_ENABLED_KEY] = enabled }
    }

    suspend fun setInsightsNotify(enabled: Boolean) {
        dataStore.edit { it[INSIGHTS_NOTIFY_KEY] = enabled }
    }

    /** The user tapped "Not now" on Home's one-time Gemma offer (Settings still has the download). */
    val gemmaOfferDismissed: Flow<Boolean> = dataStore.data.map { it[GEMMA_OFFER_DISMISSED_KEY] ?: false }

    suspend fun dismissGemmaOffer() {
        dataStore.edit { it[GEMMA_OFFER_DISMISSED_KEY] = true }
    }

    suspend fun setLastSmsScanAt(timestamp: Long) {
        dataStore.edit { it[LAST_SMS_SCAN_KEY] = timestamp }
    }

    private companion object {
        val CURRENCY_KEY = stringPreferencesKey("currency")
        val SMS_IMPORT_KEY = booleanPreferencesKey("sms_import_enabled")
        val LAST_SMS_SCAN_KEY = longPreferencesKey("last_sms_scan_at")
        val AI_ENABLED_KEY = booleanPreferencesKey("ai_enabled")
        val INSIGHTS_NOTIFY_KEY = booleanPreferencesKey("insights_notify")
        val GEMMA_OFFER_DISMISSED_KEY = booleanPreferencesKey("gemma_offer_dismissed")
    }
}

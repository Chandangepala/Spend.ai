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

    /** Inbox messages up to this time have already been scanned; null before the first scan. */
    val lastSmsScanAt: Flow<Long?> = dataStore.data.map { it[LAST_SMS_SCAN_KEY] }

    suspend fun setCurrency(currency: AppCurrency) {
        dataStore.edit { it[CURRENCY_KEY] = currency.code }
    }

    suspend fun setSmsImportEnabled(enabled: Boolean) {
        dataStore.edit { it[SMS_IMPORT_KEY] = enabled }
    }

    suspend fun setLastSmsScanAt(timestamp: Long) {
        dataStore.edit { it[LAST_SMS_SCAN_KEY] = timestamp }
    }

    private companion object {
        val CURRENCY_KEY = stringPreferencesKey("currency")
        val SMS_IMPORT_KEY = booleanPreferencesKey("sms_import_enabled")
        val LAST_SMS_SCAN_KEY = longPreferencesKey("last_sms_scan_at")
    }
}

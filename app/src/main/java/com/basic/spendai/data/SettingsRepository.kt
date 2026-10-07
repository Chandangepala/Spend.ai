package com.basic.spendai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

    suspend fun setCurrency(currency: AppCurrency) {
        dataStore.edit { it[CURRENCY_KEY] = currency.code }
    }

    private companion object {
        val CURRENCY_KEY = stringPreferencesKey("currency")
    }
}

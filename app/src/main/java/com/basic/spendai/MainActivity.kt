package com.basic.spendai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.home.HomeScreen
import com.basic.spendai.ui.settings.SettingsScreen
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpendaiTheme {
                val viewModel: ExpenseViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var showSettings by rememberSaveable { mutableStateOf(false) }

                CompositionLocalProvider(LocalCurrency provides state.currency) {
                    if (showSettings) {
                        SettingsScreen(
                            selectedCurrency = state.currency,
                            onCurrencySelected = viewModel::setCurrency,
                            onBack = { showSettings = false },
                        )
                    } else {
                        HomeScreen(viewModel, onOpenSettings = { showSettings = true })
                    }
                }
            }
        }
    }
}

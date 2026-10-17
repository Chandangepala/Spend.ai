package com.basic.spendai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.app.SpendaiApp
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

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.scanSms()
                    viewModel.refreshInsights()
                }

                CompositionLocalProvider(LocalCurrency provides state.currency) {
                    SpendaiApp(viewModel)
                }
            }
        }
    }
}

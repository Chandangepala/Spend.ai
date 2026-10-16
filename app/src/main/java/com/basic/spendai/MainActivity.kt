package com.basic.spendai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.ask.AskScreen
import com.basic.spendai.ui.detected.DetectedTransactionsScreen
import com.basic.spendai.ui.home.HomeScreen
import com.basic.spendai.ui.settings.SettingsScreen
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency

private enum class Screen { Home, Settings, Detected, Ask }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SpendaiTheme {
                val viewModel: ExpenseViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val aiState by viewModel.aiState.collectAsStateWithLifecycle()
                val insightsNotify by viewModel.insightsNotify.collectAsStateWithLifecycle()
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.scanSms()
                    viewModel.refreshInsights()
                }

                CompositionLocalProvider(LocalCurrency provides state.currency) {
                    when (screen) {
                        Screen.Settings -> {
                            LaunchedEffect(Unit) { viewModel.refreshAiStatus() }
                                SettingsScreen(
                                selectedCurrency = state.currency,
                                onCurrencySelected = viewModel::setCurrency,
                                onBack = { screen = Screen.Home },
                                smsImportEnabled = state.smsImportEnabled,
                                onSmsImportChange = viewModel::setSmsImportEnabled,
                                ai = aiState,
                                onAiEnabledChange = viewModel::setAiEnabled,
                                onDownloadAi = viewModel::downloadAiModel,
                                insightsNotify = insightsNotify,
                                onInsightsNotifyChange = viewModel::setInsightsNotify,
                            )
                        }

                        Screen.Detected -> {
                            val hidden by viewModel.hiddenDetections.collectAsStateWithLifecycle()
                            DetectedTransactionsScreen(
                                detections = state.pendingDetections,
                                onAccept = viewModel::acceptDetection,
                                onDismiss = viewModel::dismissDetection,
                                onAcceptAll = viewModel::acceptAllDetections,
                                onBack = { screen = Screen.Home },
                                hidden = hidden,
                                onRestore = viewModel::restoreDetection,
                                onRename = viewModel::renameDetection,
                                findDuplicate = viewModel::findDuplicate,
                            )
                        }

                        Screen.Ask -> {
                            val history by viewModel.askHistory.collectAsStateWithLifecycle()
                            val asking by viewModel.asking.collectAsStateWithLifecycle()
                            AskScreen(history, asking, onAsk = viewModel::ask, onBack = { screen = Screen.Home })
                        }

                        Screen.Home -> HomeScreen(
                            viewModel,
                            onOpenSettings = { screen = Screen.Settings },
                            onOpenDetected = { screen = Screen.Detected },
                            onOpenAsk = { screen = Screen.Ask },
                        )
                    }
                }
            }
        }
    }
}

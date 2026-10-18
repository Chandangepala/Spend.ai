package com.basic.spendai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.basic.spendai.reminders.ReminderReceiver
import com.basic.spendai.reminders.ReminderTarget
import com.basic.spendai.ui.ExpenseViewModel
import com.basic.spendai.ui.app.SpendaiApp
import com.basic.spendai.ui.motion.LocalReducedMotion
import com.basic.spendai.ui.motion.reducedMotion
import com.basic.spendai.ui.theme.SpendaiTheme
import com.basic.spendai.util.LocalCurrency

class MainActivity : ComponentActivity() {
    /** Where a tapped daily reminder asked to go, until the app has gone there. */
    private var openTarget by mutableStateOf<ReminderTarget?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Not again after a rotation: the reminder was already handled.
        if (savedInstanceState == null) openTarget = ReminderReceiver.targetOf(intent)
        enableEdgeToEdge()
        setContent {
            SpendaiTheme {
                val viewModel: ExpenseViewModel = viewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var reduceMotion by remember { mutableStateOf(reducedMotion(this)) }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    viewModel.scanSms()
                    viewModel.refreshInsights()
                    // The user is here now; a reminder still in the shade would be stale.
                    ReminderReceiver.dismiss(this@MainActivity)
                    // Re-read on every resume so toggling "Remove animations" applies straight away.
                    reduceMotion = reducedMotion(this@MainActivity)
                }

                CompositionLocalProvider(LocalCurrency provides state.currency, LocalReducedMotion provides reduceMotion) {
                    SpendaiApp(viewModel, openTarget, onOpened = { openTarget = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openTarget = ReminderReceiver.targetOf(intent)
    }
}

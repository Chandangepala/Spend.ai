package com.basic.spendai.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.basic.spendai.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Picks up bank SMS as they arrive, even when the app isn't open. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val now = System.currentTimeMillis()
        // A long SMS arrives as several parts from the same sender.
        val messages = parts.filterNotNull()
            .groupBy { it.originatingAddress.orEmpty() }
            .map { (sender, pieces) -> RawSms(sender, pieces.joinToString("") { it.messageBody.orEmpty() }, now) }

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (SettingsRepository(context).smsImportEnabled.first()) {
                    SmsImportRepository(context).process(messages)
                    SmsEnrichWorker.enqueueIfUseful(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

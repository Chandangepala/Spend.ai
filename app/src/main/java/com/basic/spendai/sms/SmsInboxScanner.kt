package com.basic.spendai.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.basic.spendai.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Reads inbox SMS that arrived since the last scan (30 days back on the first run). */
object SmsInboxScanner {
    private val FIRST_SCAN_WINDOW_MS = TimeUnit.DAYS.toMillis(30)

    suspend fun scan(context: Context): Int = withContext(Dispatchers.IO) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return@withContext 0

        val settings = SettingsRepository(context)
        // Taken before the query so a message arriving mid-scan is picked up next time.
        val now = System.currentTimeMillis()
        val since = settings.lastSmsScanAt.first() ?: (now - FIRST_SCAN_WINDOW_MS)

        val messages = mutableListOf<RawSms>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { cursor ->
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                messages += RawSms(
                    sender = cursor.getString(address).orEmpty(),
                    body = cursor.getString(body) ?: continue,
                    timestamp = cursor.getLong(date),
                )
            }
        }

        val added = SmsImportRepository(context).process(messages)
        settings.setLastSmsScanAt(now)
        added
    }
}

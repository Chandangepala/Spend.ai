package com.basic.spendai.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.basic.spendai.MainActivity
import com.basic.spendai.R
import com.basic.spendai.budgets.BudgetMath
import com.basic.spendai.data.ExpenseDatabase
import com.basic.spendai.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * The daily reminder: an alarm at the user's chosen time, re-armed after each one, after a reboot
 * and when the clock or time zone changes. Each time it fires it looks at the day's data and posts
 * a notification only if there's something to add or review ([DailyReminder.message]).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_REMIND) remind(context.applicationContext)
                reschedule(context.applicationContext)
            } catch (e: Exception) {
                Log.w(TAG, "reminder failed", e)
            } finally {
                result.finish()
            }
        }
    }

    private suspend fun remind(context: Context) {
        val settings = SettingsRepository(context)
        if (!settings.reminderEnabled.first()) return
        val db = ExpenseDatabase.get(context)
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        // Two alarms back each other up (see schedule); only the first one each day reminds.
        if (settings.lastReminderDay.first() == today.toString()) return
        settings.setLastReminderDay(today.toString())
        val monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val month = db.expenseDao().between(monthStart, now.toEpochMilli() + 1)
        val pending = db.detectedTransactionDao().observePending().first()
        val budgets = db.budgetDao().observeBudgets().first()
            .map { BudgetMath.progress(month, it.category, it.monthlyLimit, now, zone) }
        val message = DailyReminder.message(
            loggedToday = month.count { it.timestamp >= dayStart },
            pendingCount = pending.size,
            pendingTotal = pending.sumOf { it.amount },
            budgets = budgets,
            currency = settings.currency.first(),
        )
        if (message == null) Log.i(TAG, "up to date today; no reminder") else notify(context, message)
    }

    companion object {
        private const val TAG = "SpendAI-Reminder"
        private const val ACTION_REMIND = "com.basic.spendai.action.DAILY_REMINDER"
        private const val CHANNEL_ID = "daily_reminder"
        internal const val NOTIFICATION_ID = 7
        private const val EXTRA_OPEN = "com.basic.spendai.extra.OPEN"

        /** Arms (or clears) the alarm from the saved settings. Safe to call any time. */
        suspend fun reschedule(context: Context) {
            val settings = SettingsRepository(context)
            if (settings.reminderEnabled.first()) {
                schedule(context, settings.reminderMinutes.first() ?: DailyReminder.DEFAULT_MINUTES)
            } else {
                val alarms = context.getSystemService(AlarmManager::class.java)
                alarms.cancel(alarmIntent(context, ON_TIME))
                alarms.cancel(alarmIntent(context, WHILE_IDLE))
            }
        }

        /**
         * Without the exact-alarm permission Android gives no single alarm that is both punctual and
         * works in Doze, so both are set for the same time: one due within 10 minutes (held back
         * while the phone dozes), and one that fires even in Doze but may come up to an hour late.
         */
        private fun schedule(context: Context, minutes: Int) {
            val at = DailyReminder.nextTrigger(minutes, ZonedDateTime.now()).toInstant().toEpochMilli()
            val alarms = context.getSystemService(AlarmManager::class.java)
            alarms.setWindow(AlarmManager.RTC_WAKEUP, at, ON_TIME_WINDOW_MS, alarmIntent(context, ON_TIME))
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarmIntent(context, WHILE_IDLE))
        }

        private const val ON_TIME = 0
        private const val WHILE_IDLE = 1
        private const val ON_TIME_WINDOW_MS = 10 * 60_000L

        private fun alarmIntent(context: Context, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Where a tapped reminder asked to go; null for an ordinary launch. */
        fun targetOf(intent: Intent?): ReminderTarget? =
            intent?.getStringExtra(EXTRA_OPEN)?.let { name -> ReminderTarget.entries.firstOrNull { it.name == name } }

        /** Clears a shown reminder once the app is open: the user is already here. */
        fun dismiss(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

        internal fun notify(context: Context, message: ReminderMessage) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A nudge at your chosen time to add or review the day's spending."
                }
            )
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_reminder)
                .setContentTitle(message.title)
                .setContentText(message.text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
                .setContentIntent(openIntent(context, message.target))
                .setAutoCancel(true)
            // Reviewing comes first, but adding a cash spend should be one tap away too.
            if (message.target == ReminderTarget.REVIEW) {
                builder.addAction(0, "Add expense", openIntent(context, ReminderTarget.ADD))
            }
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        }

        private fun openIntent(context: Context, target: ReminderTarget): PendingIntent = PendingIntent.getActivity(
            context, target.ordinal + 1,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN, target.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

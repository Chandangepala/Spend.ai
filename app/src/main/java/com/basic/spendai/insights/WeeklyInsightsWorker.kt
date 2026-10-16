package com.basic.spendai.insights

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.basic.spendai.MainActivity
import com.basic.spendai.R
import com.basic.spendai.ai.InsightWriter
import com.basic.spendai.data.SettingsRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Weekly, while charging: computes the week's findings (pure code, no model) and, if the user
 * opted in, posts a notification. The friendly summary is written later, when the app is opened.
 */
class WeeklyInsightsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val report = InsightsRepository(applicationContext).ensureCurrent() ?: return Result.success()
        val settings = SettingsRepository(applicationContext)
        if (report.count > 0 && settings.insightsNotify.first()) {
            val input = InsightWriter.Input(report, settings.currency.first())
            notify(applicationContext, "Your week, ${InsightWriter.range(input)}", InsightWriter.template(input))
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "weekly-insights"
        private const val CHANNEL_ID = "weekly_summary"
        private const val NOTIFICATION_ID = 6

        /** Safe to call on every launch: KEEP leaves an existing schedule alone. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<WeeklyInsightsWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresCharging(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        private fun notify(context: Context, title: String, text: String) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A short summary of your week's spending, worked out on this phone."
                }
            )
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_insights)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        }
    }
}

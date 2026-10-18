package com.basic.spendai.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.basic.spendai.ai.AiProviders
import com.basic.spendai.ai.AiTier
import com.basic.spendai.ai.GemmaModel

/**
 * Tidies newly detected SMS (merchant, category, "not a transaction") with Gemma while the app is
 * closed. Without Gemma nothing runs here: Gemini Nano only works in the foreground, so those
 * rows wait for the next app open, as before.
 */
class SmsEnrichWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val provider = AiProviders.background(applicationContext)
        if (provider.tier == AiTier.GEMMA) SmsImportRepository(applicationContext).enrichPending(provider)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "sms-enrich"

        /** Queues a pass if Gemma is installed; a pass already running finishes first. */
        fun enqueueIfUseful(context: Context) {
            if (!GemmaModel.isInstalled(context)) return
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, OneTimeWorkRequestBuilder<SmsEnrichWorker>().build())
        }
    }
}

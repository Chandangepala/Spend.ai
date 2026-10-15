package com.basic.spendai.ai

import android.util.Log
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * Gemini Nano through ML Kit's Prompt API. Runs fully on device, but only on phones with
 * AICore and only while SpendAI is the foreground app (background calls get BACKGROUND_USE_BLOCKED).
 */
class NanoProvider : AiProvider {
    override val tier = AiTier.NANO

    private val model: GenerativeModel? by lazy {
        runCatching { Generation.getClient() }.onFailure { Log.w(TAG, "Prompt API client unavailable", it) }.getOrNull()
    }

    override suspend fun status(): AiStatus {
        val client = model ?: return AiStatus.Unavailable
        return try {
            when (client.checkStatus()) {
                FeatureStatus.AVAILABLE -> AiStatus.Available
                FeatureStatus.DOWNLOADABLE -> AiStatus.Downloadable()
                FeatureStatus.DOWNLOADING -> AiStatus.Downloading(0, null)
                else -> AiStatus.Unavailable
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "checkStatus failed", e)
            AiStatus.Unavailable
        }
    }

    override fun download(): Flow<AiStatus> {
        val client = model ?: return flow { emit(AiStatus.Unavailable) }
        return flow {
            var total: Long? = null
            client.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted -> {
                        total = status.bytesToDownload
                        emit(AiStatus.Downloading(0, total))
                    }
                    is DownloadStatus.DownloadProgress -> emit(AiStatus.Downloading(status.totalBytesDownloaded, total))
                    DownloadStatus.DownloadCompleted -> emit(AiStatus.Available)
                    is DownloadStatus.DownloadFailed -> emit(AiStatus.Downloadable(status.e.message ?: "Download failed"))
                }
            }
        }.catch { e ->
            if (e is CancellationException) throw e
            Log.w(TAG, "download failed", e)
            emit(AiStatus.Downloadable(e.message ?: "Download failed"))
        }
    }

    override suspend fun generate(prompt: String, maxTokens: Int): String? {
        val client = model ?: return null
        val request = generateContentRequest(TextPart(prompt)) {
            // Low temperature: we want the same JSON for the same SMS, not creativity.
            temperature = 0.1f
            topK = 10
            maxOutputTokens = maxTokens
        }
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return client.generateContent(request).candidates.firstOrNull()?.text
            } catch (e: CancellationException) {
                throw e
            } catch (e: GenAiException) {
                if (e.errorCode != GenAiException.ErrorCode.BUSY || attempt == MAX_ATTEMPTS - 1) {
                    Log.w(TAG, "generate failed with code ${e.errorCode}", e)
                    return null
                }
                delay(e.retryDelay?.toMillis() ?: (500L shl attempt))
            } catch (e: Exception) {
                Log.w(TAG, "generate failed", e)
                return null
            }
        }
        return null
    }

    private companion object {
        const val TAG = "SpendAI-Nano"
        const val MAX_ATTEMPTS = 3
    }
}

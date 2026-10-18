package com.basic.spendai.ai

import android.content.Context
import com.basic.spendai.data.SettingsRepository
import kotlinx.coroutines.flow.first

/** Picks the best tier this phone can run right now: Gemini Nano, then Gemma, else rules. */
object AiProviders {
    val nano: NanoProvider by lazy { NanoProvider() }

    @Volatile
    private var gemma: GemmaProvider? = null

    fun gemma(context: Context): GemmaProvider =
        gemma ?: synchronized(this) { gemma ?: GemmaProvider(context).also { gemma = it } }

    /** For the foreground (the app is on screen). */
    suspend fun best(context: Context): AiProvider {
        val enabled = SettingsRepository(context).aiEnabled.first()
        return when (choose(enabled, nano.status() == AiStatus.Available, GemmaModel.isInstalled(context))) {
            AiTier.NANO -> nano
            AiTier.GEMMA -> gemma(context)
            AiTier.RULES -> RulesOnlyProvider
        }
    }

    /** For workers and receivers: Nano refuses background calls, so only Gemma can help here. */
    suspend fun background(context: Context): AiProvider {
        val enabled = SettingsRepository(context).aiEnabled.first()
        return if (choose(enabled, nanoAvailable = false, GemmaModel.isInstalled(context)) == AiTier.GEMMA) gemma(context) else RulesOnlyProvider
    }

    /** Nano when available (built into the phone, fastest), else an installed Gemma, else rules. */
    fun choose(enabled: Boolean, nanoAvailable: Boolean, gemmaInstalled: Boolean): AiTier = when {
        !enabled -> AiTier.RULES
        nanoAvailable -> AiTier.NANO
        gemmaInstalled -> AiTier.GEMMA
        else -> AiTier.RULES
    }
}

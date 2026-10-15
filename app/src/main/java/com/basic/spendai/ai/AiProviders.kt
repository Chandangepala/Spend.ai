package com.basic.spendai.ai

import android.content.Context
import com.basic.spendai.data.SettingsRepository
import kotlinx.coroutines.flow.first

/** Picks the best tier this phone can run right now: Nano, (later) Gemma, else rules. */
object AiProviders {
    val nano: NanoProvider by lazy { NanoProvider() }

    suspend fun best(context: Context): AiProvider {
        if (!SettingsRepository(context).aiEnabled.first()) return RulesOnlyProvider
        if (nano.status() == AiStatus.Available) return nano
        return RulesOnlyProvider
    }
}

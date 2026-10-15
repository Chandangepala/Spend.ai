package com.basic.spendai.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Which "brain" answered. Every feature must still work on [RULES]. */
enum class AiTier { NANO, GEMMA, RULES }

sealed interface AiStatus {
    data object Available : AiStatus
    /** The model can be fetched; [error] is set when the last download failed. */
    data class Downloadable(val error: String? = null) : AiStatus
    data class Downloading(val bytesDone: Long, val bytesTotal: Long?) : AiStatus
    data object Unavailable : AiStatus
}

/**
 * A text-in, text-out model. Implementations never throw: any failure (busy, quota,
 * app in background, unsupported device) comes back as null so callers fall back to rules.
 */
interface AiProvider {
    val tier: AiTier

    suspend fun status(): AiStatus

    suspend fun generate(prompt: String, maxTokens: Int = 256): String?

    /** Fetches the model if the tier needs one; emits progress and ends on the final status. */
    fun download(): Flow<AiStatus> = flow { emit(status()) }
}

/** The no-AI tier: features use their deterministic rules instead. */
object RulesOnlyProvider : AiProvider {
    override val tier = AiTier.RULES
    override suspend fun status(): AiStatus = AiStatus.Unavailable
    override suspend fun generate(prompt: String, maxTokens: Int): String? = null
}

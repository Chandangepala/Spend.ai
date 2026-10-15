package com.basic.spendai.ai

import com.basic.spendai.data.Category

/**
 * [fromAi]: the value came from a validated model reply.
 * [replied]: the model answered at all; false means it was unavailable (busy, quota, background, off),
 * so the caller may want to retry later rather than settle for the rules result.
 */
data class AiResult<O>(val value: O, val fromAi: Boolean, val replied: Boolean = fromAi)

/**
 * One narrow AI job: build a short prompt, ask the provider, validate the reply against facts
 * the code already knows, and otherwise use the deterministic [fallback]. The model never
 * decides money; [parse] must reject any reply that disagrees with the input.
 */
abstract class AiTask<I, O> {
    protected open val maxTokens: Int = 256

    protected abstract fun prompt(input: I): String

    /** Decode and validate; null means "don't trust this reply". */
    protected abstract fun parse(reply: String, input: I): O?

    protected abstract fun fallback(input: I): O

    suspend fun run(provider: AiProvider, input: I): AiResult<O> {
        val reply = provider.generate(prompt(input), maxTokens)
        val parsed = reply?.let { runCatching { parse(it, input) }.getOrNull() }
        return if (parsed != null) {
            AiResult(parsed, fromAi = true, replied = true)
        } else {
            AiResult(fallback(input), fromAi = false, replied = reply != null)
        }
    }

    companion object {
        /** The fixed list every prompt offers; replies outside it are rejected. */
        val CATEGORY_NAMES: String = Category.entries.joinToString(", ") { it.name }

        fun categoryOrNull(name: String?): Category? =
            Category.entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) }
    }
}

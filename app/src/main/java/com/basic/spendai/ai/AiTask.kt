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

    /** False when the rules already cover this input fully; the model isn't called (no wait). */
    protected open fun needsModel(input: I): Boolean = true

    suspend fun run(provider: AiProvider, input: I): AiResult<O> {
        // replied = true: nothing was left undone, so there's nothing to retry later.
        if (!needsModel(input)) return AiResult(fallback(input), fromAi = false, replied = true)
        val reply = provider.generate(prompt(input), maxTokens)
        val parsed = reply?.let { runCatching { parse(it, input) }.getOrNull() }
        return if (parsed != null) {
            AiResult(parsed, fromAi = true, replied = true)
        } else {
            AiResult(fallback(input), fromAi = false, replied = reply != null)
        }
    }

    /** For prompt evaluation on a device: the exact prompt, its token budget, and how a raw reply is judged. */
    internal fun promptFor(input: I): String = prompt(input)
    internal val replyTokens: Int get() = maxTokens
    internal fun asksModel(input: I): Boolean = needsModel(input)
    internal fun judge(reply: String, input: I): O? = runCatching { parse(reply, input) }.getOrNull()

    companion object {
        /** The fixed list every prompt offers (as users see it); replies outside it are rejected. */
        val CATEGORY_NAMES: String = Category.entries.joinToString(", ") { it.label }

        /** Accepts the label ("Dining & Drinks", "dining and drinks") or the stored name ("Food"). */
        fun categoryOrNull(name: String?): Category? {
            val key = name?.let(::normalize)?.takeIf { it.isNotEmpty() } ?: return null
            return Category.entries.firstOrNull { normalize(it.label) == key || normalize(it.name) == key }
        }

        private fun normalize(text: String) =
            text.lowercase().replace(" and ", " ").replace("&", " ").filter(Char::isLetter)
    }
}

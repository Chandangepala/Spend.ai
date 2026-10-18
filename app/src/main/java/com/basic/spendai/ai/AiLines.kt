package com.basic.spendai.ai

/**
 * Reads "item -> answer" lines. Prompts for small models end on "<first item> ->" so the model
 * only has to write the answer; it sometimes repeats "<item> ->" anyway, which is accepted.
 */
object AiLines {
    /** The first [count] answers in order, or null if the reply has fewer lines than that. */
    fun answers(reply: String, firstItem: String, count: Int): List<String>? {
        val lines = ("$firstItem ->" + reply).lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < count) return null
        return lines.take(count).map { it.substringAfterLast("->").trim().trim('"', '.', ',') }
    }
}

package com.basic.spendai.ai

import kotlinx.serialization.json.Json

/** Reads JSON out of a model reply, which may wrap it in prose or ``` fences. */
object AiJson {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /** Decoded value, or null when the reply has no valid JSON of the expected shape. */
    inline fun <reified T> decode(reply: String?): T? {
        val text = reply?.let(::extract) ?: return null
        return runCatching { json.decodeFromString<T>(text) }.getOrNull()
    }

    /** The first balanced {...} or [...] block in [reply], ignoring brackets inside strings. */
    fun extract(reply: String): String? {
        val start = reply.indexOfFirst { it == '{' || it == '[' }
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until reply.length) {
            val c = reply[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                inString -> Unit
                c == '{' || c == '[' -> depth++
                c == '}' || c == ']' -> if (--depth == 0) return reply.substring(start, i + 1)
            }
        }
        return null
    }
}

package com.basic.spendai.sms

import com.basic.spendai.data.Category
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.sqrt

/** The model's best category for a text, and how sure it is (0..1). */
data class ModelGuess(val category: Category, val confidence: Float)

/**
 * A small built-in text classifier for categories: a linear model over hashed words and letter
 * fragments, trained on the developer's machine from a list of common merchants and spend phrases
 * (tools/category-model). It ships inside the app (~450 KB), works on every phone, offline, in
 * well under a millisecond, and never learns from or sends anything about the user.
 *
 * The features must match tools/category-model/train.py exactly; CategoryModelTest checks that the
 * Kotlin and Python versions score the held-out phrases the same.
 */
class CategoryModel private constructor(
    private val dim: Int,
    private val classes: List<Category>,
    private val scale: Float,
    private val bias: FloatArray,
    /** [dim][classes], row-major, int8 (times [scale]). */
    private val weights: ByteArray,
) {
    /** Null when the text has no letters to go on. */
    fun predict(text: String): ModelGuess? {
        val counts = HashMap<Int, Float>()
        for (feature in features(text)) {
            val bucket = ((fnv1a(feature).toLong() and 0xFFFFFFFFL) % dim).toInt()
            counts[bucket] = (counts[bucket] ?: 0f) + 1f
        }
        if (counts.isEmpty()) return null
        val norm = sqrt(counts.values.sumOf { (it * it).toDouble() }).toFloat()
        val n = classes.size
        val scores = bias.copyOf()
        for ((bucket, count) in counts) {
            val value = count / norm * scale
            val row = bucket * n
            for (c in 0 until n) scores[c] += value * weights[row + c]
        }
        // Softmax, shifted by the max for stability.
        val max = scores.max()
        val exps = scores.map { exp((it - max).toDouble()) }
        val total = exps.sum()
        val best = exps.indices.maxBy { exps[it] }
        return ModelGuess(classes[best], (exps[best] / total).toFloat())
    }

    companion object {
        /** The model bundled with the app; null if it couldn't be read (rules still work). */
        val bundled: CategoryModel? by lazy {
            runCatching { CategoryModel::class.java.getResourceAsStream("/category_model.bin")?.use(::read) }.getOrNull()
        }

        fun read(input: InputStream): CategoryModel {
            val buffer = ByteBuffer.wrap(input.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val magic = ByteArray(4).also(buffer::get)
            require(String(magic, Charsets.US_ASCII) == "SPCM") { "not a category model" }
            require(buffer.int == 1) { "unsupported model version" }
            val dim = buffer.int
            val classCount = buffer.int
            val classes = List(classCount) {
                val name = String(ByteArray(buffer.int).also(buffer::get), Charsets.UTF_8)
                Category.entries.firstOrNull { it.name == name } ?: Category.Other
            }
            val scale = buffer.float
            val bias = FloatArray(classCount) { buffer.float }
            val weights = ByteArray(dim * classCount).also(buffer::get)
            return CategoryModel(dim, classes, scale, bias, weights)
        }

        /** Lowercase letters only: digits and symbols become spaces ("PAYTM*BLINKIT4412" → "paytm blinkit"). */
        internal fun normalize(text: String): List<String> =
            text.lowercase().map { if (it.isLetter()) it else ' ' }.joinToString("").split(' ').filter { it.isNotEmpty() }

        /** Words, word pairs, and 3- and 4-letter fragments of each word (with ^ and $ marking its ends). */
        internal fun features(text: String): List<String> {
            val words = normalize(text)
            val out = ArrayList<String>()
            words.forEach { out += "w:$it" }
            words.zipWithNext { a, b -> out += "b:$a $b" }
            for (word in words) {
                val padded = "^$word$"
                for (n in 3..4) {
                    for (i in 0..padded.length - n) out += "c:" + padded.substring(i, i + n)
                }
            }
            return out
        }

        internal fun fnv1a(text: String): Int {
            var hash = 0x811C9DC5.toInt()
            for (byte in text.toByteArray(Charsets.UTF_8)) {
                hash = hash xor (byte.toInt() and 0xFF)
                hash *= 0x01000193
            }
            return hash
        }
    }
}

package com.basic.spendai.ai

import kotlin.math.abs

/** Guards model-written text: it may only repeat numbers it was given ("₹4,200" matches "4,200.00"). */
object NumberCheck {
    private val numberRegex = Regex("""\d[\d,]*(?:\.\d+)?""")

    fun numbersIn(text: String): List<Double> =
        numberRegex.findAll(text).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()

    fun onlyKnownNumbers(text: String, source: String): Boolean {
        val allowed = numbersIn(source)
        return numbersIn(text).all { n -> allowed.any { abs(it - n) < 0.005 } }
    }
}

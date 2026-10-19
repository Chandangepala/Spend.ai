package com.basic.spendai.sms

import com.basic.spendai.data.Category
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CategoryModelTest {
    private val model = checkNotNull(CategoryModel.bundled) { "category_model.bin missing from resources" }

    /** tools/category-model/data/eval.txt: phrases the model never trained on. */
    private val heldOut: List<Pair<Category, String>> by lazy {
        var category: Category? = null
        File("../tools/category-model/data/eval.txt").readLines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .flatMap { line ->
                Regex("""\[(\w+)]""").matchEntire(line)?.let { header ->
                    category = Category.valueOf(header.groupValues[1])
                    emptyList()
                } ?: line.split(",").map { it.trim() }.filter { it.isNotEmpty() }.map { category!! to it }
            }
    }

    @After
    fun restoreModel() {
        CategoryGuesser.model = { CategoryModel.bundled }
    }

    @Test
    fun kotlinScoresTheHeldOutPhrasesExactlyLikeTrainPy() {
        val right = heldOut.count { (category, text) -> model.predict(text)?.category == category }
        // train.py prints "quantized held-out accuracy"; update this when the model is retrained.
        assertEquals(271, right)
        assertEquals(337, heldOut.size)
    }

    @Test
    fun featuresMatchTheTrainingScript() {
        assertEquals(listOf("paytm", "blinkit"), CategoryModel.normalize("PAYTM*BLINKIT4412"))
        assertEquals(
            listOf("w:tea", "c:^te", "c:tea", "c:ea$", "c:^tea", "c:tea$"),
            CategoryModel.features("Tea"),
        )
        // FNV-1a 32-bit reference value for "a".
        assertEquals(0xE40C292C.toInt(), CategoryModel.fnv1a("a"))
    }

    @Test
    fun theModelFillsInWhereKeywordsHaveNothing() {
        val rulesOnly = score(useModel = false)
        val withModel = score(useModel = true)
        println("Held-out ${heldOut.size}: rules right $rulesOnly, rules + model right $withModel")
        assertTrue("model should help: $rulesOnly -> $withModel", withModel > rulesOnly + heldOut.size / 5)
    }

    @Test
    fun spendsTheKeywordsMiss() {
        assertEquals(Category.Groceries, CategoryGuesser.guessCasual("Mg Grocery Store"))
        assertEquals(Category.Food, CategoryGuesser.guessCasual("popcorn"))
        assertEquals(Category.Health, CategoryGuesser.guessCasual("dentist"))
        assertEquals(Category.Shopping, CategoryGuesser.guess("Lenskart", "Rs 2,000 debited from A/c XX12"))
    }

    @Test
    fun peoplesNamesStayOther() {
        // A payment to a person could be anything; the model says Other rather than guess.
        assertEquals(Category.Other, CategoryGuesser.guess("Meena", "INR 170.00 debited DCB a/c 2820; to MEENA"))
        assertEquals(Category.Other, CategoryGuesser.guess("Ramesh Kumar", ""))
    }

    private fun score(useModel: Boolean): Int {
        CategoryGuesser.model = { if (useModel) CategoryModel.bundled else null }
        return heldOut.count { (category, text) -> CategoryGuesser.guessCasual(text) == category }
    }
}

package com.basic.spendai.ai

import com.basic.spendai.data.Category
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiTaskTest {

    @Serializable
    private data class Reply(val category: String)

    /** Minimal task: categorize a merchant, falling back to Other. */
    private object CategorizeTask : AiTask<String, Category>() {
        override fun prompt(input: String) = "Category of $input? One of: $CATEGORY_NAMES. JSON {\"category\":...}"
        override fun parse(reply: String, input: String) = categoryOrNull(AiJson.decode<Reply>(reply)?.category)
        override fun fallback(input: String) = Category.Other
    }

    @Test
    fun usesValidatedModelReply() = runTest {
        val provider = FakeAiProvider("""{"category":"food"}""")
        val result = CategorizeTask.run(provider, "Swiggy")
        assertEquals(Category.Food, result.value)
        assertTrue(result.fromAi)
        assertTrue(provider.prompts.single().contains("Dining & Drinks, Groceries, Transport"))
    }

    @Test
    fun fallsBackWhenReplyIsInvalid() = runTest {
        val result = CategorizeTask.run(FakeAiProvider("""{"category":"Snacks"}"""), "Swiggy")
        assertEquals(Category.Other, result.value)
        assertFalse(result.fromAi)
    }

    @Test
    fun acceptsLabelsAndStoredNames() {
        assertEquals(Category.Food, AiTask.categoryOrNull("Dining & Drinks"))
        assertEquals(Category.Food, AiTask.categoryOrNull("dining and drinks"))
        assertEquals(Category.Food, AiTask.categoryOrNull("Food"))
        assertEquals(Category.PersonalCare, AiTask.categoryOrNull("Personal Care"))
        assertEquals(Category.Finance, AiTask.categoryOrNull("EMI & Finance"))
        assertEquals(null, AiTask.categoryOrNull("Snacks"))
        assertEquals(null, AiTask.categoryOrNull(""))
    }

    @Test
    fun fallsBackWhenModelFailsOrIsOff() = runTest {
        assertFalse(CategorizeTask.run(FakeAiProvider(null), "Swiggy").fromAi)
        assertEquals(Category.Other, CategorizeTask.run(RulesOnlyProvider, "Swiggy").value)
    }
}

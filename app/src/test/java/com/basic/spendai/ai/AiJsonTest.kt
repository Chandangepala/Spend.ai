package com.basic.spendai.ai

import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiJsonTest {

    @Serializable
    data class Reply(val merchant: String, val category: String)

    @Test
    fun decodesPlainJson() {
        assertEquals(Reply("Swiggy", "Food"), AiJson.decode<Reply>("""{"merchant":"Swiggy","category":"Food"}"""))
    }

    @Test
    fun findsJsonWrappedInProseAndFences() {
        val reply = "Sure! Here you go:\n```json\n{\"merchant\": \"Swiggy\", \"category\": \"Food\", \"extra\": 1}\n```\nHope that helps."
        assertEquals(Reply("Swiggy", "Food"), AiJson.decode<Reply>(reply))
    }

    @Test
    fun ignoresBracketsInsideStrings() {
        assertEquals("""{"merchant":"A}B[","category":"Other"}""", AiJson.extract("""x {"merchant":"A}B[","category":"Other"} y"""))
    }

    @Test
    fun decodesArrays() {
        assertEquals(listOf(1, 2), AiJson.decode<List<Int>>("Numbers: [1, 2]"))
    }

    @Test
    fun garbageOrWrongShapeIsNull() {
        assertNull(AiJson.decode<Reply>(null))
        assertNull(AiJson.decode<Reply>("I can't help with that."))
        assertNull(AiJson.decode<Reply>("""{"merchant": "Swiggy" """))
        assertNull(AiJson.decode<Reply>("""{"name":"Swiggy"}"""))
    }
}

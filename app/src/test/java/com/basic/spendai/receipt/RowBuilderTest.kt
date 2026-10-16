package com.basic.spendai.receipt

import com.basic.spendai.ai.ReceiptItemizer
import org.junit.Assert.assertEquals
import org.junit.Test

class RowBuilderTest {

    @Test
    fun joinsNameAndPriceColumnsBackIntoRows() {
        // What ML Kit typically returns for a receipt: a names column block, then a prices column block.
        val lines = listOf(
            OcrLine("DMART", 300, 10, 50),
            OcrLine("Rice 5kg", 20, 100, 130),
            OcrLine("Shampoo", 20, 140, 170),
            OcrLine("Grand Total", 20, 200, 232),
            OcrLine("450.00", 600, 102, 131),
            OcrLine("220.00", 600, 141, 169),
            OcrLine("1,240.00", 600, 203, 233),
        )
        val text = RowBuilder.join(lines)
        assertEquals("DMART\nRice 5kg   450.00\nShampoo   220.00\nGrand Total   1,240.00", text)
        assertEquals(1240.0, ReceiptItemizer.Rules.findTotal(text)!!, 0.001)
    }

    @Test
    fun slightlyTiltedRowsStillJoin() {
        val lines = listOf(OcrLine("TOTAL", 20, 100, 130), OcrLine("638.00", 600, 110, 140))
        assertEquals("TOTAL   638.00", RowBuilder.join(lines))
    }
}

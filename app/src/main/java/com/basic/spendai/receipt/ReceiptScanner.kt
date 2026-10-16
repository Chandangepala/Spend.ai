package com.basic.spendai.receipt

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** On-device OCR for receipt photos (ML Kit, bundled model, works offline). */
object ReceiptScanner {
    private const val TAG = "SpendAI-Receipt"

    /** Receipt text as visual rows ("Grand Total   1,240.00"), or null if nothing could be read. */
    suspend fun readText(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val image = try {
            // Reads EXIF rotation, so sideways phone photos still read correctly.
            InputImage.fromFilePath(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't open receipt image", e)
            return@withContext null
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { cont ->
                recognizer.process(image)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener {
                        Log.w(TAG, "Text recognition failed", it)
                        cont.resume(null)
                    }
            }
            text?.let(::toRows)?.takeIf { it.isNotBlank() }
        } finally {
            recognizer.close()
        }
    }

    /**
     * ML Kit groups text into blocks, and receipts often come back as a names column and a prices
     * column. Re-join lines that sit at the same height so labels and amounts share a row.
     */
    private fun toRows(text: Text): String {
        val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            line.boundingBox?.let { box -> OcrLine(line.text, box.left, box.top, box.bottom) }
        }
        return RowBuilder.join(lines)
    }
}

/** One OCR line and where it sits on the photo. */
data class OcrLine(val text: String, val left: Int, val top: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/** Pure row-joining logic, kept separate so it can be unit-tested without ML Kit. */
object RowBuilder {
    fun join(lines: List<OcrLine>): String {
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in lines.sortedBy { it.centerY }) {
            val row = rows.lastOrNull()
            // Same row when the centres are within half a line height of each other.
            if (row != null && kotlin.math.abs(row.first().centerY - line.centerY) <= maxOf(row.first().height, line.height) / 2) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString("   ") { it.text.trim() } }
    }
}

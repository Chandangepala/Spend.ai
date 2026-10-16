package com.basic.spendai.receipt

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** Where "Take photo" saves receipt pictures: app cache only, removed right after OCR. */
object ReceiptPhotos {
    private fun dir(context: Context) = File(context.cacheDir, "receipts").apply { mkdirs() }

    fun newPhotoUri(context: Context): Uri {
        val file = File(dir(context), "receipt-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    /** Deletes the photo if it's one of ours; gallery picks are left alone. */
    fun delete(context: Context, uri: Uri) {
        if (uri.authority != "${context.packageName}.files") return
        uri.lastPathSegment?.let { File(dir(context), it).delete() }
    }
}

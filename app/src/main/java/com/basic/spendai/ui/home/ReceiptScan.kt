package com.basic.spendai.ui.home

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.basic.spendai.ai.ReceiptResult
import com.basic.spendai.receipt.ReceiptPhotos
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency

/**
 * "Take photo" uses the system camera app, so SpendAI needs no camera permission;
 * "Choose photo" uses the Android photo picker, which shares only the one photo picked.
 */
@Composable
fun ReceiptScanRow(scanning: Boolean, error: String?, onPhoto: (Uri) -> Unit) {
    val context = LocalContext.current
    // Saved across process death: the camera app may run long enough for Android to kill us.
    var pendingPhoto by rememberSaveable { mutableStateOf<String?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto?.let(Uri::parse)
        pendingPhoto = null
        if (uri != null) {
            if (saved) onPhoto(uri) else ReceiptPhotos.delete(context, uri)
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhoto)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Scan a receipt", style = MaterialTheme.typography.titleMedium)
        if (scanning) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Reading receipt…", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val uri = ReceiptPhotos.newPhotoUri(context)
                    pendingPhoto = uri.toString()
                    try {
                        takePicture.launch(uri)
                    } catch (_: ActivityNotFoundException) {
                        pendingPhoto = null
                        ReceiptPhotos.delete(context, uri)
                    }
                }) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Take photo")
                }
                OutlinedButton(onClick = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Choose photo")
                }
            }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** Header above a scanned receipt's review list: where it's from, and one expense vs. its items. */
@Composable
fun ReceiptHeader(receipt: ReceiptResult, asItems: Boolean, onAsItemsChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            buildString {
                append(receipt.merchant ?: "Receipt")
                receipt.total?.let { append(" · Total ${formatCurrency(it, LocalCurrency.current)}") }
            },
            style = MaterialTheme.typography.titleMedium,
        )
        if (receipt.total == null) {
            Text(
                "Couldn't find the total on this receipt. Please enter it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (receipt.items.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !asItems, onClick = { onAsItemsChange(false) }, label = { Text("One expense") })
                FilterChip(selected = asItems, onClick = { onAsItemsChange(true) }, label = { Text("${receipt.items.size} items") })
            }
        }
    }
}

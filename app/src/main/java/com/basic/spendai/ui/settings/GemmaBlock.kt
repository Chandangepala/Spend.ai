package com.basic.spendai.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.basic.spendai.ui.components.ClayButton
import com.basic.spendai.ui.components.ClayOutlinedButton
import com.basic.spendai.ai.GemmaState
import com.basic.spendai.ui.components.GemmaProgress
import com.basic.spendai.ui.components.GemmaTermsNote
import com.basic.spendai.ui.components.megabytes

/** What Settings can do with the Gemma model; [canDownload] is false until a download URL is configured. */
data class GemmaActions(
    val canDownload: Boolean = false,
    val onDownload: () -> Unit = {},
    val onImport: (Uri) -> Unit = {},
    val onCancel: () -> Unit = {},
    val onDelete: () -> Unit = {},
)

/** Install, progress, or remove the optional Gemma model. Shown only where Gemini Nano can't run. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GemmaBlock(state: GemmaState, actions: GemmaActions) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(actions.onImport) }
    var confirmDelete by remember { mutableStateOf(false) }

    HorizontalDivider(Modifier.padding(top = 14.dp, bottom = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Gemma model (optional)", style = MaterialTheme.typography.titleSmall)
        when (state) {
            is GemmaState.NotInstalled -> {
                Text(
                    "Adds AI on phones without Gemini Nano. It's a one-time ${megabytes(584_000_000)} file that stays on this phone. " +
                        "With it, new SMS and your weekly summary are tidied even while the app is closed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                    if (actions.canDownload) {
                        ClayButton(onClick = actions.onDownload) { Text("Download (Wi-Fi)", style = MaterialTheme.typography.labelLarge) }
                    }
                    ClayOutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import model file", style = MaterialTheme.typography.labelLarge) }
                }
                GemmaTermsNote()
                if (!actions.canDownload) {
                    Text(
                        "Pick the Gemma 3 1B file (ending in .litertlm) you downloaded to this phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is GemmaState.Copying -> GemmaProgress(state, actions.onCancel)
            is GemmaState.Installed -> {
                Text(
                    state.loadError ?: "Installed · ${megabytes(state.bytes)}. The first answer after a while takes a few seconds while it loads.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.loadError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { confirmDelete = true }) { Text("Delete model") }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the Gemma model?") },
            text = { Text("This frees about 584 MB. SpendAI goes back to smart rules; your expenses aren't affected.") },
            confirmButton = {
                TextButton(onClick = {
                    actions.onDelete()
                    confirmDelete = false
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

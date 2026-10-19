package com.basic.spendai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.basic.spendai.ui.components.ClayButton
import com.basic.spendai.ai.GemmaModel
import com.basic.spendai.ai.GemmaState
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.GemmaProgress
import com.basic.spendai.ui.components.GemmaTermsNote
import com.basic.spendai.ui.components.megabytes

/**
 * One tap to get on-device AI on phones without Gemini Nano: the download runs in the background
 * (Wi-Fi only) and AI features switch on by themselves when it's done.
 */
@Composable
fun GemmaOfferCard(state: GemmaState, onDownload: () -> Unit, onNotNow: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(8.dp))
                Text("Smarter AI for this phone", style = MaterialTheme.typography.titleMedium)
            }
            when (state) {
                is GemmaState.Copying -> GemmaProgress(state, onCancel)
                else -> {
                    Text(
                        "Get tidier SMS, better quick entry and answers. One download of " +
                            "${megabytes(GemmaModel.EXPECTED_BYTES)} on Wi-Fi; it runs on your phone and nothing is sent anywhere.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    (state as? GemmaState.NotInstalled)?.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    GemmaTermsNote()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ClayButton(onClick = onDownload) {
                            Text(if ((state as? GemmaState.NotInstalled)?.error != null) "Try again" else "Download", style = MaterialTheme.typography.labelLarge)
                        }
                        TextButton(onClick = onNotNow) { Text("Not now") }
                    }
                }
            }
        }
    }
}

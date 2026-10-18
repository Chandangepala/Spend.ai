package com.basic.spendai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import com.basic.spendai.ai.GemmaState

/** "Megabytes" the way phones show storage (1 MB = 1,000,000 bytes). */
fun megabytes(bytes: Long) = "${bytes / 1_000_000} MB"

/** "Downloading… 120 MB of 584 MB" / "Waiting for Wi-Fi…" / "Checking the file…". */
fun gemmaProgressText(state: GemmaState.Copying): String = when {
    state.verifying -> "Checking the file…"
    state.waitingForWifi -> "Waiting for Wi-Fi… The download starts when you're connected."
    else -> (if (state.downloading) "Downloading… " else "Copying… ") +
        (state.bytesTotal?.let { "${megabytes(state.bytesDone)} of ${megabytes(it)}" } ?: megabytes(state.bytesDone))
}

/** Progress line, bar and Cancel for a running Gemma download or import. */
@Composable
fun GemmaProgress(state: GemmaState.Copying, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            gemmaProgressText(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        val fraction = state.bytesTotal?.takeIf { it > 0 && !state.waitingForWifi && !state.verifying }?.let { state.bytesDone.toFloat() / it }
        if (fraction != null) {
            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (!state.verifying) TextButton(onClick = onCancel) { Text("Cancel") }
    }
}

/** The notice Gemma's terms ask redistributors to pass on, with a link to the terms. */
@Composable
fun GemmaTermsNote(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val text = buildAnnotatedString {
        append("Gemma is provided under and subject to the ")
        withLink(
            LinkAnnotation.Url(
                GEMMA_TERMS_URL,
                TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline)),
            ) { uriHandler.openUri(GEMMA_TERMS_URL) }
        ) { append("Gemma Terms of Use") }
        append(".")
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

const val GEMMA_TERMS_URL = "https://ai.google.dev/gemma/terms"

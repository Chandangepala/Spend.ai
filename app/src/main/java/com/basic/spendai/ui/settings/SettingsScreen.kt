package com.basic.spendai.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.basic.spendai.ai.AiStatus
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.ui.AiUiState
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.clay
import com.basic.spendai.ui.theme.SpendaiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedCurrency: AppCurrency,
    onCurrencySelected: (AppCurrency) -> Unit,
    onBack: () -> Unit,
    smsImportEnabled: Boolean = false,
    onSmsImportChange: (Boolean) -> Unit = {},
    ai: AiUiState = AiUiState(),
    onAiEnabledChange: (Boolean) -> Unit = {},
    onDownloadAi: () -> Unit = {},
    insightsNotify: Boolean = false,
    onInsightsNotifyChange: (Boolean) -> Unit = {},
    /** False inside the app's navigation, which handles system back (with predictive back). */
    handleSystemBack: Boolean = true,
) {
    if (handleSystemBack) BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 640.dp)
                .selectableGroup(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                    Text("On-device AI", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your financial SMS never leaves your phone. All AI runs on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                AiRow(ai = ai, onEnabledChange = onAiEnabledChange, onDownload = onDownloadAi)
            }
            item {
                SmsImportRow(enabled = smsImportEnabled, onChange = onSmsImportChange)
            }
            item {
                InsightsNotifyRow(enabled = insightsNotify, onChange = onInsightsNotifyChange)
            }
            item {
                Column(Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp)) {
                    Text("Currency", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Choose the currency your expenses are shown in. Existing amounts keep their numbers and are not converted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(AppCurrency.entries, key = { it.code }) { currency ->
                CurrencyRow(
                    currency = currency,
                    selected = currency == selectedCurrency,
                    onClick = { onCurrencySelected(currency) },
                )
            }
        }
    }
}

@Composable
private fun AiRow(ai: AiUiState, onEnabledChange: (Boolean) -> Unit, onDownload: () -> Unit) {
    val status = ai.status
    val (headline, detail) = when (status) {
        AiStatus.Available -> "Gemini Nano ready" to "Smarter categories and merchant names, processed on device."
        is AiStatus.Downloadable ->
            "Gemini Nano available to download" to (status.error ?: "A one-time download managed by Android. Wi-Fi recommended.")
        is AiStatus.Downloading -> "Downloading Gemini Nano…" to downloadDetail(status)
        AiStatus.Unavailable -> "Not supported on this device" to "Using smart rules instead. Everything still works."
    }

    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 6.dp) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = ai.enabled, role = Role.Switch, onValueChange = onEnabledChange),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Use on-device AI", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (ai.enabled) headline else "Off. Using smart rules only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(checked = ai.enabled, onCheckedChange = null)
            }
            if (ai.enabled) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                when (status) {
                    is AiStatus.Downloadable -> Button(onClick = onDownload, modifier = Modifier.padding(top = 10.dp)) {
                        Text(if (status.error != null) "Retry download" else "Download model")
                    }
                    is AiStatus.Downloading -> {
                        val fraction = status.bytesTotal?.takeIf { it > 0 }?.let { status.bytesDone.toFloat() / it }
                        val barModifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                        if (fraction != null) {
                            LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) }, modifier = barModifier)
                        } else {
                            LinearProgressIndicator(modifier = barModifier)
                        }
                    }
                    else -> Unit
                }
            }
            AccuracyLine(ai)
        }
    }
}

/** "Suggestions you kept unchanged (last 30 days): AI 87% of 23 · Rules 40% of 15". Local-only numbers. */
@Composable
private fun AccuracyLine(ai: AiUiState) {
    val parts = buildList {
        if (ai.aiAccuracy.total > 0) add("AI ${ai.aiAccuracy.percent}% of ${ai.aiAccuracy.total}")
        if (ai.rulesAccuracy.total > 0) add("Rules ${ai.rulesAccuracy.percent}% of ${ai.rulesAccuracy.total}")
    }
    if (parts.isEmpty()) return
    Text(
        "Suggestions you kept unchanged (last 30 days): ${parts.joinToString(" · ")}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

private fun downloadDetail(status: AiStatus.Downloading): String {
    val doneMb = status.bytesDone / 1_000_000
    val totalMb = status.bytesTotal?.let { it / 1_000_000 }
    return if (totalMb != null && totalMb > 0) "$doneMb of $totalMb MB" else "Starting…"
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun SmsImportRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val permissions = rememberMultiplePermissionsState(
        listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
    ) { results -> if (results.values.all { it }) onChange(true) }
    val checked = enabled && permissions.allPermissionsGranted

    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = { on ->
                        if (on && !permissions.allPermissionsGranted) {
                            permissions.launchMultiplePermissionRequest()
                        } else {
                            onChange(on)
                        }
                    },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Auto-detect expenses from SMS", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Reads bank debit SMS and suggests expenses for you to review. Messages are processed on this device and never leave it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/** Weekly summary notification; on Android 13+ turning it on first asks for notification permission. */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun InsightsNotifyRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val needsPermission = Build.VERSION.SDK_INT >= 33
    val permission = if (needsPermission) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { granted -> if (granted) onChange(true) }
    } else {
        null
    }
    val granted = permission == null || permission.status.isGranted
    val checked = enabled && granted

    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 6.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = { on -> if (on && !granted) permission?.launchPermissionRequest() else onChange(on) },
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Weekly summary notification", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Once a week, while charging, a short note on what stood out in your spending. Worked out on this phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(12.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

@Composable
private fun CurrencyRow(currency: AppCurrency, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val surface = if (selected) colors.primaryContainer else colors.surface

    ClayCard(
        color = surface,
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        elevation = if (selected) 10.dp else 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clay(lerp(surface, colors.primary, 0.15f), cornerRadius = 24.dp, elevation = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    currency.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.primary,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(currency.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    currency.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            RadioButton(selected = selected, onClick = null)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    SpendaiTheme {
        SettingsScreen(selectedCurrency = AppCurrency.INR, onCurrencySelected = {}, onBack = {})
    }
}

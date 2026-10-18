package com.basic.spendai.ui.settings

import android.Manifest
import android.content.Context
import android.os.Build
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.basic.spendai.ui.ReminderUiState
import com.basic.spendai.ui.components.ClayCard
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import java.util.Date
import java.util.TimeZone

/**
 * Daily reminder: on/off, the time, and a suggested time from the user's own spending. On
 * Android 13+ turning it on first asks for notification permission.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
internal fun ReminderRow(
    reminder: ReminderUiState,
    onEnabledChange: (Boolean) -> Unit,
    onTimeChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val permission = if (Build.VERSION.SDK_INT >= 33) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) { granted -> if (granted) onEnabledChange(true) }
    } else {
        null
    }
    val granted = permission == null || permission.status.isGranted
    val checked = reminder.enabled && granted
    var picking by rememberSaveable { mutableStateOf(false) }
    val time = formatMinutes(context, reminder.minutes)

    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 6.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        role = Role.Switch,
                        onValueChange = { on -> if (on && !granted) permission?.launchPermissionRequest() else onEnabledChange(on) },
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Daily reminder", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (checked) {
                            "At $time, only on days you haven't added anything or have SMS spends to review."
                        } else {
                            "A nudge at a time you choose to add or review the day's spending."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(checked = checked, onCheckedChange = null)
            }
            if (checked) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button, onClickLabel = "Change reminder time") { picking = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Time", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(time, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                val suggested = reminder.suggested
                if (suggested != null && suggested != reminder.minutes) {
                    Row(
                        modifier = Modifier.padding(start = 16.dp, end = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Suggested: ${formatMinutes(context, suggested)}, just after your spending usually ends for the day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onTimeChange(suggested) }) { Text("Use") }
                    }
                }
            }
        }
    }

    if (picking) {
        ReminderTimeDialog(
            minutes = reminder.minutes,
            is24Hour = DateFormat.is24HourFormat(context),
            onConfirm = {
                onTimeChange(it)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(minutes: Int, is24Hour: Boolean, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reminder time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "9:30 PM" or "21:30", following the phone's 12/24-hour setting. */
internal fun formatMinutes(context: Context, minutes: Int): String =
    DateFormat.getTimeFormat(context).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(minutes * 60_000L))

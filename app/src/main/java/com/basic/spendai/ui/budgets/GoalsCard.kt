package com.basic.spendai.ui.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.basic.spendai.ai.CoachWriter
import com.basic.spendai.budgets.GoalMath
import com.basic.spendai.data.SavingsGoal
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.motion.growFraction
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Savings goals: how far along each is, and what it takes per month to finish on time. */
@Composable
fun GoalsCard(
    goals: List<SavingsGoal>,
    onAdd: () -> Unit,
    onEdit: (SavingsGoal) -> Unit,
    onAddMoney: (SavingsGoal) -> Unit,
    onCoach: Coach,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth(), cornerRadius = 28.dp, elevation = 10.dp) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(Modifier.padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Savings goals", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onAdd) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Add")
                }
            }
            if (goals.isEmpty()) {
                Text(
                    "Saving up for something? Add a goal and a date, and SpendAI works out how much to put aside each month.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }
            goals.forEach { goal -> GoalRow(goal, today, { onEdit(goal) }, { onAddMoney(goal) }, onCoach) }
        }
    }
}

@Composable
private fun GoalRow(goal: SavingsGoal, today: LocalDate, onEdit: () -> Unit, onAddMoney: () -> Unit, onCoach: Coach) {
    val currency = LocalCurrency.current
    val deadline = goal.deadlineEpochDay?.let(LocalDate::ofEpochDay)
    val plan = GoalMath.plan(goal.saved, goal.target, deadline, today)
    val shown = growFraction(plan.fraction.toFloat())
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit goal", onClick = onEdit)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { shown },
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round,
                )
                Text("${(plan.fraction * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(goal.name, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${formatCurrency(goal.saved, currency)} of ${formatCurrency(goal.target, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                deadline?.let {
                    Text(
                        "By ${it.format(dateFormat)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        CoachLine(CoachWriter.Topic.Goal(goal.name, plan, deadline), onCoach)
        if (!plan.done) {
            FilledTonalButton(onClick = onAddMoney) { Text("Add money") }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** Add or edit a goal: name, target, amount saved so far, and an optional date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalDialog(editing: SavingsGoal?, onSave: (SavingsGoal) -> Unit, onDelete: (SavingsGoal) -> Unit, onDismiss: () -> Unit) {
    val currency = LocalCurrency.current
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var target by remember { mutableStateOf(editing?.target?.let(::plainAmount) ?: "") }
    var saved by remember { mutableStateOf(editing?.saved?.takeIf { it > 0 }?.let(::plainAmount) ?: "") }
    var deadline by remember { mutableStateOf(editing?.deadlineEpochDay?.let(LocalDate::ofEpochDay)) }
    var picking by remember { mutableStateOf(false) }
    val targetValue = parseMoney(target)
    val savedValue = if (saved.isBlank()) 0.0 else saved.replace(",", "").toDoubleOrNull()?.takeIf { it >= 0 }
    val valid = name.isNotBlank() && targetValue != null && savedValue != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "Edit goal" else "New savings goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("What for?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    target, { target = it },
                    label = { Text("Target") },
                    prefix = { Text(currency.javaCurrency.getSymbol()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    saved, { saved = it },
                    label = { Text("Saved so far") },
                    prefix = { Text(currency.javaCurrency.getSymbol()) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { picking = true }) {
                        Icon(Icons.Rounded.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(deadline?.let { "By ${it.format(dateFormat)}" } ?: "Add a date (optional)")
                    }
                    if (deadline != null) TextButton(onClick = { deadline = null }) { Text("Clear") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val goal = (editing ?: SavingsGoal(name = "", target = 0.0)).copy(
                        name = name.trim(),
                        target = targetValue!!,
                        saved = savedValue!!,
                        deadlineEpochDay = deadline?.toEpochDay(),
                    )
                    onSave(goal)
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = {
            if (editing != null) {
                TextButton(onClick = {
                    onDelete(editing)
                    onDismiss()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (picking) {
        // The picker works in UTC midnights; a goal's date is a plain calendar day.
        val today = remember { LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadline?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis > today
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { deadline = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/** Adds an amount to a goal's savings. */
@Composable
fun AddMoneyDialog(goal: SavingsGoal, onAdd: (Double) -> Unit, onDismiss: () -> Unit) {
    val currency = LocalCurrency.current
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to ${goal.name}") },
        text = {
            OutlinedTextField(
                amount, { amount = it },
                label = { Text("Amount") },
                prefix = { Text(currency.javaCurrency.getSymbol()) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = parseMoney(amount) != null, onClick = {
                parseMoney(amount)?.let(onAdd)
                onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

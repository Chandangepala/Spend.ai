package com.basic.spendai.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.basic.spendai.ai.CoachWriter
import com.basic.spendai.data.Category
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.ClayChip
import com.basic.spendai.ui.components.ClayFieldShape
import com.basic.spendai.ui.components.clayField
import com.basic.spendai.ui.components.clayFieldColors
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency

private val CUTS = listOf(10, 20, 30, 50)

/** "What if I spent less on…?": the saving is computed from the average month; the coach words it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfCard(
    /** Most-used first; the first is picked to start with. */
    categories: List<Category>,
    onWhatIf: suspend (Category, Int) -> CoachWriter.Topic.WhatIf?,
    onCoach: Coach,
    /** Bumped when expenses change, so the numbers follow. */
    dataVersion: Int,
    modifier: Modifier = Modifier,
) {
    val currency = LocalCurrency.current
    var category by rememberSaveable { mutableStateOf(categories.first()) }
    var percent by rememberSaveable { mutableIntStateOf(20) }
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    var result by remember { mutableStateOf<Result?>(null) }
    LaunchedEffect(category, percent, dataVersion) {
        result = Result(onWhatIf(category, percent))
    }

    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("What if you spent less?", style = MaterialTheme.typography.titleMedium)
            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = category.label,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    leadingIcon = { Icon(category.icon, contentDescription = null, tint = category.color) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(menuOpen) },
                    singleLine = true,
                    shape = ClayFieldShape,
                    colors = clayFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .clayField(),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    categories.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.label) },
                            leadingIcon = { Icon(c.icon, contentDescription = null, tint = c.color, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                category = c
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CUTS.forEach { cut ->
                    ClayChip(selected = percent == cut, onClick = { percent = cut }, label = "−$cut%")
                }
            }
            val r = result ?: return@Column
            val topic = r.topic
            Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (topic == null) {
                    Text(
                        "No ${category.label} spending in your last 3 full months to work from.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatCurrency(topic.monthly, currency),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            " a month · ${formatCurrency(topic.yearly, currency)} a year",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    CoachLine(topic, onCoach)
                }
            }
        }
    }
}

/** Wraps "no history" (null) so it differs from "not loaded yet". */
private data class Result(val topic: CoachWriter.Topic.WhatIf?)

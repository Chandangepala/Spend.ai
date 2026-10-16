package com.basic.spendai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category

/** How many categories show before "+ More". */
const val VISIBLE_CATEGORIES = 7

/** Most-used first (ties keep the standard order); Other always last. */
fun orderCategories(usage: Map<Category, Int>): List<Category> =
    Category.entries
        .filter { it != Category.Other }
        .sortedByDescending { usage[it] ?: 0 } + Category.Other

/**
 * The user's 7 most-used categories, then "+ More (7)" to reveal the rest in place.
 * Starts expanded when the selected category isn't among the 7, so it's always visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryChips(
    selected: Category,
    onSelect: (Category) -> Unit,
    order: List<Category>,
    modifier: Modifier = Modifier,
) {
    val top = order.filter { it != Category.Other }.take(VISIBLE_CATEGORIES)
    var expanded by rememberSaveable { mutableStateOf(selected !in top) }
    val shown = if (expanded) order else top
    val hidden = order.size - top.size

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        shown.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.label) },
                leadingIcon = {
                    Icon(option.icon, contentDescription = null, tint = if (option == selected) Color.White else option.color)
                },
                shape = RoundedCornerShape(16.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = option.color,
                    selectedLabelColor = Color.White,
                    labelColor = option.color,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = option == selected,
                    borderColor = option.color.copy(alpha = 0.5f),
                ),
            )
        }
        if (hidden > 0) {
            AssistChip(
                onClick = { expanded = !expanded },
                label = { Text(if (expanded) "Less" else "+ More ($hidden)") },
                trailingIcon = {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null)
                },
                shape = RoundedCornerShape(16.dp),
            )
        }
    }
}

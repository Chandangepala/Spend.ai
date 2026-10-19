package com.basic.spendai.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.basic.spendai.data.Category
import com.basic.spendai.ui.components.ClayChip
import com.basic.spendai.ui.components.ClayPill
import androidx.compose.foundation.layout.size

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
    // A category picked for the user (from the title) may be one of the hidden ones: show it.
    LaunchedEffect(selected) { if (selected !in top) expanded = true }
    val shown = if (expanded) order else top
    val hidden = order.size - top.size

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Selected: emerald with white text. Unselected: white clay, the category colour only on the icon
        // (text never takes the category colour; amber or slate text was hard to read).
        shown.forEach { option ->
            ClayChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = option.label,
                icon = option.icon,
                iconTint = option.color,
            )
        }
        if (hidden > 0) {
            ClayPill(
                text = if (expanded) "Less" else "+ More ($hidden)",
                onClick = { expanded = !expanded },
                trailing = {
                    Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
        }
    }
}

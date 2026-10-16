package com.basic.spendai.ui.home

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.basic.spendai.insights.WeeklyReport
import com.basic.spendai.ui.components.ClayCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Home needs to show the weekly summary. */
data class InsightUi(
    val report: WeeklyReport,
    val summary: String,
    val fromAi: Boolean,
    /** One line per finding, with the exact numbers behind the summary. */
    val details: List<String>,
)

/** "This week" card: the summary (3 lines until tapped), and the findings behind it under "Details". */
@Composable
fun InsightCard(insight: InsightUi, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    // Only offer the toggle when there's something more to show.
    var truncated by remember { mutableStateOf(false) }
    val expandable = insight.details.isNotEmpty() || truncated || expanded
    val colors = MaterialTheme.colorScheme
    ClayCard(color = colors.surface, modifier = modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 6.dp) {
        Column(
            Modifier
                .animateContentSize()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Insights, contentDescription = null, tint = colors.primary)
                Spacer(Modifier.width(8.dp))
                Text("This week", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (insight.fromAi) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "Written by on-device AI", tint = colors.primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text(range(insight.report), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                insight.summary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow },
                modifier = Modifier.padding(end = 8.dp, bottom = if (expandable) 0.dp else 12.dp),
            )
            if (expanded) {
                insight.details.forEach { line ->
                    Text("• $line", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp))
                }
            }
            if (expandable) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.align(Alignment.End)) {
                    Text(if (expanded) "Less" else if (insight.details.isEmpty()) "More" else "Details")
                }
            }
        }
    }
}

private fun range(report: WeeklyReport): String {
    val fmt = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
    val zone = ZoneId.systemDefault()
    val from = Instant.ofEpochMilli(report.from).atZone(zone).toLocalDate()
    val to = Instant.ofEpochMilli(report.until - 1).atZone(zone).toLocalDate()
    return "${from.format(fmt)} – ${to.format(fmt)}"
}

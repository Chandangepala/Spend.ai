package com.basic.spendai.ui.ask

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.basic.spendai.stats.Metric
import com.basic.spendai.stats.PeriodStats
import com.basic.spendai.ui.AskEntry
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.util.LocalCurrency
import com.basic.spendai.util.formatCurrency

/** Questions that the no-AI rules answer well, so they work on every phone. */
private val suggestions = listOf(
    "How much did I spend this month?",
    "Food last month vs this month",
    "Where did I spend the most this month?",
    "How many expenses last week?",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskScreen(history: List<AskEntry>, asking: Boolean, onAsk: (String) -> Unit, onBack: (() -> Unit)? = null) {
    // As a tab there's no back arrow; the navigation handles system back.
    onBack?.let { BackHandler(onBack = it) }
    var question by rememberSaveable { mutableStateOf("") }
    fun send(text: String) {
        if (text.isBlank() || asking) return
        onAsk(text.trim())
        question = ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ask your spends", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") } }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 720.dp),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    placeholder = { Text("e.g. Food last month vs this month") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    trailingIcon = {
                        if (asking) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            IconButton(onClick = { send(question) }, enabled = question.isNotBlank()) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(question) }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s -> SuggestionChip(onClick = { send(s) }, label = { Text(s) }, enabled = !asking) }
                }
            }
            if (history.isEmpty()) {
                item {
                    Text(
                        "Answers are worked out on this phone from your own expenses.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(history, key = { it.id }) { entry -> AnswerCard(entry, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun AnswerCard(entry: AskEntry, modifier: Modifier = Modifier) {
    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth(), cornerRadius = 24.dp, elevation = 8.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.question, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.sentence, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.answer.query.describe(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (entry.fromAi) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("On-device AI", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            // The exact numbers, always shown, so the sentence can be checked at a glance.
            NumbersRow(entry.answer.primary, entry.answer.query.metric)
            entry.answer.comparison?.let { NumbersRow(it, entry.answer.query.metric) }
        }
    }
}

@Composable
private fun NumbersRow(stats: PeriodStats, metric: Metric) {
    val currency = LocalCurrency.current
    Column {
        Row {
            Text(stats.period.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(formatCurrency(stats.total, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Text(
            buildString {
                append("${stats.count} ${if (stats.count == 1) "expense" else "expenses"}")
                if (stats.count > 0) append(" · average ${formatCurrency(stats.average, currency)}")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (metric == Metric.TOP_MERCHANTS) {
            stats.topMerchants.forEach { (name, amount) ->
                Row(Modifier.padding(start = 8.dp, top = 2.dp)) {
                    Text(name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(formatCurrency(amount, currency), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

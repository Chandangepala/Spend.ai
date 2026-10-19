package com.basic.spendai.ui.ask

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.basic.spendai.stats.Metric
import com.basic.spendai.stats.PeriodStats
import com.basic.spendai.ui.AskEntry
import com.basic.spendai.ui.components.AiBadge
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.ClayFieldShape
import com.basic.spendai.ui.components.ClayPill
import com.basic.spendai.ui.components.TabHeader
import com.basic.spendai.ui.components.clayField
import com.basic.spendai.ui.components.clayFieldColors
import androidx.compose.material3.FilledIconButton
import com.basic.spendai.ui.motion.LocalReducedMotion
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
fun AskScreen(
    history: List<AskEntry>,
    asking: Boolean,
    onAsk: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
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
            // As a tab: the same header as Activity and Insights. Opened on its own: a back arrow.
            if (onBack == null) {
                TabHeader("Ask", onOpenSettings)
            } else {
                TopAppBar(
                    title = { Text("Ask your spends", style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
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
                    shape = ClayFieldShape,
                    colors = clayFieldColors(),
                    trailingIcon = {
                        if (asking) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            // A round emerald send button, filled once there's something to send.
                            FilledIconButton(onClick = { send(question) }, enabled = question.isNotBlank(), modifier = Modifier.padding(end = 4.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(question) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayField(),
                )
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s -> ClayPill(text = s, onClick = { send(s) }) }
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
            if (asking) item(key = "thinking") { ThinkingCard(Modifier.animateItem()) }
            items(history, key = { it.id }) { entry ->
                // New answers slide up into place; with reduced motion they just appear.
                val reduced = LocalReducedMotion.current
                val visible = remember { MutableTransitionState(reduced).apply { targetState = true } }
                AnimatedVisibility(visibleState = visible, enter = slideInVertically { it / 3 } + fadeIn(), modifier = Modifier.animateItem()) {
                    AnswerCard(entry)
                }
            }
        }
    }
}

/** Three dots pulsing in turn while the answer is worked out; static dots with reduced motion. */
@Composable
private fun ThinkingCard(modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val transition = rememberInfiniteTransition(label = "thinking")
    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .semantics { contentDescription = "Working out your answer" },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { i ->
                val alpha = if (reduced) 0.6f else transition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(450, delayMillis = i * 150), RepeatMode.Reverse),
                    label = "dot$i",
                ).value
                Box(
                    Modifier
                        .size(8.dp)
                        .graphicsLayer { this.alpha = alpha }
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
        }
    }
}

@Composable
private fun AnswerCard(entry: AskEntry, modifier: Modifier = Modifier) {
    ClayCard(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(entry.question, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(entry.sentence, style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.answer.query.describe(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                if (entry.fromAi) AiBadge(description = "Written by on-device AI", label = "On-device AI")
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

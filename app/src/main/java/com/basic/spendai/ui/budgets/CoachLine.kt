package com.basic.spendai.ui.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.basic.spendai.ai.AiResult
import com.basic.spendai.ai.CoachWriter
import com.basic.spendai.ui.components.AiBadge
import com.basic.spendai.util.LocalCurrency

typealias Coach = suspend (CoachWriter.Topic) -> AiResult<String>

/** Shows the template sentence at once, then the model's wording if it passes the checks. */
@Composable
fun CoachLine(topic: CoachWriter.Topic, onCoach: Coach, modifier: Modifier = Modifier) {
    val currency = LocalCurrency.current
    val template = remember(topic, currency) { CoachWriter.template(CoachWriter.Input(topic, currency)) }
    var fromAi by remember(topic) { mutableStateOf<String?>(null) }
    LaunchedEffect(topic) { onCoach(topic).takeIf { it.fromAi }?.let { fromAi = it.value } }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Top) {
        if (fromAi != null) AiBadge("Worded by on-device AI")
        Text(fromAi ?: template, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

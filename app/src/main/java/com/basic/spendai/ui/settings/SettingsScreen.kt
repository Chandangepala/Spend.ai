package com.basic.spendai.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import com.basic.spendai.data.AppCurrency
import com.basic.spendai.ui.components.ClayCard
import com.basic.spendai.ui.components.clay
import com.basic.spendai.ui.theme.SpendaiTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    selectedCurrency: AppCurrency,
    onCurrencySelected: (AppCurrency) -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

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

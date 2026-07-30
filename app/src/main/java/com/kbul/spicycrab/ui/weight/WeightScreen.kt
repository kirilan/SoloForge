package com.kbul.spicycrab.ui.weight

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.ui.common.DateTimeField
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WeightScreen(viewModel: WeightViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.openLogSheet() },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.weight_log)) },
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.nav_weight), style = MaterialTheme.typography.headlineMedium)

            CurrentSummary(state, viewModel::toDisplay)
            RangeChips(state.range, viewModel::setRange)

            val cutoff = state.range.days?.let { System.currentTimeMillis() - it * 24L * 3600L * 1000L }
            val visible = if (cutoff != null) state.entries.filter { it.timestampEpoch >= cutoff } else state.entries
            val unit = if (state.useKg) "kg" else "lb"

            WeightChart(
                points = visible.map { ChartPoint(it.timestampEpoch, viewModel.toDisplay(it.weightKg)) },
                unitLabel = unit,
            )

            Text(stringResource(R.string.common_history), style = MaterialTheme.typography.titleMedium)

            if (state.entries.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.weight_no_entries),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp),
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        WeightRow(
                            entry = entry,
                            displayValue = viewModel.toDisplay(entry.weightKg),
                            unit = unit,
                            onClick = { viewModel.openLogSheet(entry) },
                            onDelete = { viewModel.deleteEntry(entry) },
                        )
                    }
                }
            }
        }
    }

    if (state.showLogSheet) {
        LogWeightSheet(
            initialEntry = state.entries.firstOrNull { it.id == state.editingId },
            useKg = state.useKg,
            onSave = viewModel::saveWeight,
            onDismiss = viewModel::dismissSheet,
            toDisplay = viewModel::toDisplay,
        )
    }
}

@Composable
private fun CurrentSummary(state: WeightUiState, toDisplay: (Double) -> Double) {
    val unit = if (state.useKg) "kg" else "lb"
    val latest = state.entries.firstOrNull()
    val previous = state.entries.getOrNull(1)
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (latest == null) {
                Text(stringResource(R.string.home_no_weight), style = MaterialTheme.typography.bodyLarge)
            } else {
                val curDisp = toDisplay(latest.weightKg)
                Text("${"%.1f".format(curDisp)} $unit", style = MaterialTheme.typography.displayMedium)
                if (previous != null) {
                    val delta = curDisp - toDisplay(previous.weightKg)
                    val sign = if (delta >= 0) "+" else ""
                    val color = when {
                        delta > 0 -> MaterialTheme.colorScheme.error
                        delta < 0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(
                        stringResource(R.string.weight_since_last_entry, "$sign${"%.1f".format(delta)}", unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun RangeChips(current: WeightRange, onSelect: (WeightRange) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WeightRange.entries.forEach { range ->
            FilterChip(
                selected = current == range,
                onClick = { onSelect(range) },
                label = { Text(stringResource(range.labelRes)) },
            )
        }
    }
}

@Composable
private fun WeightRow(
    entry: WeightEntry,
    displayValue: Double,
    unit: String,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm")
    val ts = formatter.format(Instant.ofEpochMilli(entry.timestampEpoch).atZone(zone))

    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("${"%.1f".format(displayValue)} $unit", style = MaterialTheme.typography.titleMedium)
                Text(ts, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (entry.note.isNotBlank()) {
                    Text(stringResource(R.string.common_quoted, entry.note), style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedButton(onClick = onClick) { Text(stringResource(R.string.common_edit)) }
            IconButton(onClick = onDelete) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.common_delete))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogWeightSheet(
    initialEntry: WeightEntry?,
    useKg: Boolean,
    onSave: (Double, String, Long) -> Unit,
    onDismiss: () -> Unit,
    toDisplay: (Double) -> Double,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var weightText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(initialEntry) {
        if (initialEntry != null) {
            weightText = "%.1f".format(toDisplay(initialEntry.weightKg))
            note = initialEntry.note
            timestamp = initialEntry.timestampEpoch
        } else {
            weightText = ""
            note = ""
            timestamp = System.currentTimeMillis()
        }
    }

    val unit = if (useKg) "kg" else "lb"
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (initialEntry != null) R.string.weight_edit_title else R.string.weight_log),
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' } },
                label = { Text(stringResource(R.string.weight_label, unit)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(R.string.weight_note_optional)) },
                modifier = Modifier.fillMaxWidth(),
            )
            DateTimeField(
                epochMillis = timestamp,
                onChange = { timestamp = it },
            )
            Button(
                onClick = {
                    val value = weightText.replace(',', '.').toDoubleOrNull() ?: return@Button
                    onSave(value, note.trim(), timestamp)
                },
                enabled = weightText.replace(',', '.').toDoubleOrNull()
                    ?.let { it.isFinite() && it > 0.0 } == true,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.common_save)) }
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.common_cancel)) }
        }
    }
}

package com.kbul.spicycrab.ui.food

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.MealPreset
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FoodScreen(viewModel: FoodViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val analyze by viewModel.analyze.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val presets by viewModel.presets.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val manualOpen by viewModel.manualOpen.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()

    when (val m = mode) {
        FoodUiMode.List -> FoodListContent(
            entries = entries,
            presets = presets,
            aiEnabled = aiEnabled,
            onAddClick = { viewModel.goToCapture() },
            onDescribeClick = { viewModel.startTextEntry() },
            onManualClick = { viewModel.openManual() },
            onRowClick = viewModel::openEdit,
            onLogPreset = viewModel::logPreset,
            onDeletePreset = viewModel::deletePreset,
        )
        FoodUiMode.Capture -> CaptureScreen(
            onCaptured = viewModel::onCaptured,
            onCancel = viewModel::onCaptureCancelled,
        )
        is FoodUiMode.Analyze -> AnalyzeScreen(
            imageFile = m.imageFile,
            state = analyze,
            onCommentChange = viewModel::onCommentChange,
            onAnalyze = viewModel::analyze,
            onRetryStronger = viewModel::retryWithStrongerModel,
            onSave = viewModel::saveEntry,
            onCancel = viewModel::cancelAnalyze,
            onEstimateUpdate = { updated -> viewModel.updateEstimate { updated } },
        )
    }

    editing?.let { editingState ->
        EditFoodSheet(
            state = editingState,
            aiEnabled = aiEnabled,
            onSave = viewModel::saveEdit,
            onDelete = viewModel::deleteEntry,
            onReanalyze = viewModel::reanalyzeEdit,
            onSaveAsPreset = viewModel::saveAsPreset,
            onDismiss = viewModel::dismissEdit,
        )
    }

    if (manualOpen) {
        ManualFoodSheet(
            onSave = viewModel::saveManual,
            onSaveAsPreset = viewModel::saveAsPreset,
            onDismiss = viewModel::dismissManual,
        )
    }
}

@Composable
private fun FoodListContent(
    entries: List<FoodEntry>,
    presets: List<MealPreset>,
    aiEnabled: Boolean,
    onAddClick: () -> Unit,
    onDescribeClick: () -> Unit,
    onManualClick: () -> Unit,
    onRowClick: (FoodEntry) -> Unit,
    onLogPreset: (MealPreset) -> Unit,
    onDeletePreset: (MealPreset) -> Unit,
) {
    Scaffold(
        floatingActionButton = {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = androidx.compose.ui.Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(
                    onClick = onManualClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.food_add_manual_cd))
                }
                if (aiEnabled) {
                    SmallFloatingActionButton(
                        onClick = onDescribeClick,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Outlined.Keyboard, contentDescription = stringResource(R.string.food_describe_cd))
                    }
                    ExtendedFloatingActionButton(
                        onClick = onAddClick,
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text(stringResource(R.string.food_analyze_photo)) },
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            QuickAddRow(
                presets = presets,
                onLog = onLogPreset,
                onDelete = onDeletePreset,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            if (entries.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(if (aiEnabled) R.string.food_empty_ai else R.string.food_empty_manual),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            ) {
                items(entries, key = { it.id }) { entry -> FoodRow(entry, onClick = { onRowClick(entry) }) }
            }
        }
    }
}

@Composable
private fun FoodRow(entry: FoodEntry, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")
    val ts = formatter.format(Instant.ofEpochMilli(entry.timestampEpoch).atZone(zone))

    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text(entry.itemName, style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.food_macros_line, entry.kcal.toInt(), entry.proteinG.toInt(), entry.carbsG.toInt(), entry.fatG.toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            val edited = entry.lastModifiedEpoch > entry.timestampEpoch
            Text(
                if (edited) stringResource(R.string.common_edited, ts) else ts,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.comment.isNotBlank()) {
                Text(stringResource(R.string.common_quoted, entry.comment), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

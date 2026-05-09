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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.data.db.entities.FoodEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FoodScreen(viewModel: FoodViewModel = hiltViewModel()) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val analyze by viewModel.analyze.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val manualOpen by viewModel.manualOpen.collectAsStateWithLifecycle()

    when (val m = mode) {
        FoodUiMode.List -> FoodListContent(
            entries = entries,
            onAddClick = { viewModel.goToCapture() },
            onManualClick = { viewModel.openManual() },
            onRowClick = viewModel::openEdit,
        )
        FoodUiMode.Capture -> CaptureScreen(
            onCaptured = viewModel::onCaptured,
            onCancel = viewModel::onCaptureCancelled,
        )
        is FoodUiMode.Analyze -> AnalyzeScreen(
            imageFile = m.imageFile,
            state = analyze,
            onCommentChange = viewModel::onCommentChange,
            onAnalyze = viewModel::analyzeImage,
            onSave = viewModel::saveEntry,
            onCancel = viewModel::cancelAnalyze,
            onEstimateUpdate = { updated -> viewModel.updateEstimate { updated } },
        )
    }

    editing?.let { editingState ->
        EditFoodSheet(
            state = editingState,
            onSave = viewModel::saveEdit,
            onDelete = viewModel::deleteEntry,
            onReanalyze = viewModel::reanalyzeEdit,
            onDismiss = viewModel::dismissEdit,
        )
    }

    if (manualOpen) {
        ManualFoodSheet(
            onSave = viewModel::saveManual,
            onDismiss = viewModel::dismissManual,
        )
    }
}

@Composable
private fun FoodListContent(
    entries: List<FoodEntry>,
    onAddClick: () -> Unit,
    onManualClick: () -> Unit,
    onRowClick: (FoodEntry) -> Unit,
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
                    Icon(Icons.Outlined.Edit, contentDescription = "Add manual meal")
                }
                ExtendedFloatingActionButton(
                    onClick = onAddClick,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Analyze photo") },
                )
            }
        }
    ) { padding ->
        if (entries.isEmpty()) {
            Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No meals logged yet. Analyze a photo or add a manual meal.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
        ) {
            items(entries, key = { it.id }) { entry -> FoodRow(entry, onClick = { onRowClick(entry) }) }
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
                "${entry.kcal.toInt()} kcal · P${entry.proteinG.toInt()} / C${entry.carbsG.toInt()} / F${entry.fatG.toInt()}",
                style = MaterialTheme.typography.bodyMedium,
            )
            val edited = entry.lastModifiedEpoch > entry.timestampEpoch
            Text(
                if (edited) "$ts · edited" else ts,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (entry.comment.isNotBlank()) {
                Text("“${entry.comment}”", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

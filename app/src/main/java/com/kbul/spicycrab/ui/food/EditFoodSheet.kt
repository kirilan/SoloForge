package com.kbul.spicycrab.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.data.db.entities.FoodEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFoodSheet(
    state: EditingState,
    onSave: (FoodEntry) -> Unit,
    onDelete: (FoodEntry) -> Unit,
    onReanalyze: (String) -> Unit,
    onSaveAsPreset: (FoodEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val entry = state.entry
    var draft by remember(entry.id, state.reanalyzeStamp) { mutableStateOf(entry) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit meal", style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = draft.itemName,
                onValueChange = { draft = draft.copy(itemName = it) },
                label = { Text("Item") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.comment,
                onValueChange = { draft = draft.copy(comment = it) },
                label = { Text("Comment") },
                modifier = Modifier.fillMaxWidth(),
            )

            ReanalyzeRow(
                hasImage = entry.imagePath != null,
                isReanalyzing = state.isReanalyzing,
                error = state.reanalyzeError,
                onReanalyze = { onReanalyze(draft.comment) },
            )

            NumField("Grams", draft.grams) { draft = draft.copy(grams = it) }
            ScaleByGramsButton(
                currentGrams = draft.grams,
                onApply = { newGrams -> draft = scaleEntry(draft, newGrams) },
                modifier = Modifier.fillMaxWidth(),
            )
            NumField("Calories", draft.kcal) { draft = draft.copy(kcal = it) }
            NumField("Protein (g)", draft.proteinG) { draft = draft.copy(proteinG = it) }
            NumField("Carbs (g)", draft.carbsG) { draft = draft.copy(carbsG = it) }
            NumField("Fat (g)", draft.fatG) { draft = draft.copy(fatG = it) }
            NumField("Fiber (g)", draft.fiberG) { draft = draft.copy(fiberG = it) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Cancel") }
                Button(
                    onClick = { onSave(draft) },
                    enabled = !state.isReanalyzing,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Save") }
            }
            var presetSaved by remember(draft.itemName) { mutableStateOf(false) }
            OutlinedButton(
                onClick = { onSaveAsPreset(draft); presetSaved = true },
                enabled = draft.itemName.isNotBlank() && !presetSaved,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (presetSaved) "Saved to quick add ✓" else "Save as preset") }

            TextButton(
                onClick = { onDelete(entry) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete entry", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun ReanalyzeRow(
    hasImage: Boolean,
    isReanalyzing: Boolean,
    error: String?,
    onReanalyze: () -> Unit,
) {
    if (!hasImage) {
        Text(
            "Re-analysis is unavailable: this entry was saved without a local photo. Enable “Save photo locally” in Settings before logging future meals.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(
            onClick = onReanalyze,
            enabled = !isReanalyzing,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            if (isReanalyzing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                    Text("Re-analyzing…")
                }
            } else {
                Text("Re-analyze with current comment")
            }
        }
        error?.let {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(0.dp))
    }
}

private fun scaleEntry(entry: FoodEntry, newGrams: Double): FoodEntry {
    if (entry.grams <= 0.0) return entry.copy(grams = newGrams)
    val r = newGrams / entry.grams
    return entry.copy(
        grams = newGrams,
        kcal = entry.kcal * r,
        proteinG = entry.proteinG * r,
        carbsG = entry.carbsG * r,
        fatG = entry.fatG * r,
        fiberG = entry.fiberG * r,
    )
}

@Composable
private fun NumField(label: String, value: Double, onChange: (Double) -> Unit) {
    OutlinedTextField(
        value = if (value == 0.0) "" else "%.1f".format(value),
        onValueChange = { txt ->
            val v = txt.replace(',', '.').toDoubleOrNull() ?: 0.0
            onChange(v)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

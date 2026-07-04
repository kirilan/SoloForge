package com.kbul.spicycrab.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.ui.common.DateTimeField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualFoodSheet(
    onSave: (FoodEntry) -> Unit,
    onSaveAsPreset: (FoodEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(emptyDraft()) }

    val canSave = draft.itemName.isNotBlank() && draft.kcal > 0.0

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add manual meal", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Enter nutrition details yourself instead of analyzing a photo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = draft.itemName,
                onValueChange = { draft = draft.copy(itemName = it) },
                label = { Text("Meal name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.comment,
                onValueChange = { draft = draft.copy(comment = it) },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )

            DateTimeField(
                epochMillis = draft.timestampEpoch,
                onChange = { draft = draft.copy(timestampEpoch = it) },
            )

            NumField("Total weight (g)", draft.grams) { draft = draft.copy(grams = it) }
            ScaleByGramsButton(
                currentGrams = draft.grams,
                onApply = { newGrams -> draft = scaleEntry(draft, newGrams) },
                modifier = Modifier.fillMaxWidth(),
            )
            NumField("Calories (kcal) *", draft.kcal) { draft = draft.copy(kcal = it) }
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
                    enabled = canSave,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Save meal") }
            }
            var presetSaved by remember(draft.itemName) { mutableStateOf(false) }
            OutlinedButton(
                onClick = { onSaveAsPreset(draft); presetSaved = true },
                enabled = canSave && !presetSaved,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(if (presetSaved) "Saved to quick add ✓" else "Save as preset") }
        }
    }
}

private fun emptyDraft(): FoodEntry = FoodEntry(
    timestampEpoch = System.currentTimeMillis(),
    lastModifiedEpoch = 0L,
    itemName = "",
    grams = 0.0,
    kcal = 0.0,
    proteinG = 0.0,
    carbsG = 0.0,
    fatG = 0.0,
    fiberG = 0.0,
    comment = "",
    modelUsed = "manual",
    confidence = "user",
    imagePath = null,
)

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

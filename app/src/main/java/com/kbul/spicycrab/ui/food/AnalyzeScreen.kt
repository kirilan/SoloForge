package com.kbul.spicycrab.ui.food

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kbul.spicycrab.domain.nutrition.NutritionEstimate
import java.io.File

@Composable
fun AnalyzeScreen(
    imageFile: File,
    state: AnalyzeState,
    onCommentChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onEstimateUpdate: (NutritionEstimate) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Review meal", style = MaterialTheme.typography.headlineMedium)
        AsyncImage(
            model = imageFile,
            contentDescription = "Captured meal",
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        OutlinedTextField(
            value = state.comment,
            onValueChange = onCommentChange,
            label = { Text("Comment (optional)") },
            placeholder = { Text("e.g. grilled chicken, ~200g, side of rice") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (state.estimate == null && !state.isLoading) {
            Button(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Analyze") }
        }
        if (state.estimate != null && !state.isLoading) {
            OutlinedButton(
                onClick = onAnalyze,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Re-analyze with current comment") }
        }
        if (state.isLoading) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(0.dp))
                Text("  Analyzing…", style = MaterialTheme.typography.bodyLarge)
            }
        }
        state.error?.let {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        state.estimate?.let { est ->
            EstimateForm(est, onEstimateUpdate)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Discard") }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Save") }
            }
        }
        if (state.estimate == null && !state.isLoading) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Discard") }
        }
    }
}

@Composable
private fun EstimateForm(
    est: NutritionEstimate,
    onUpdate: (NutritionEstimate) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ConfidenceBadge(est.confidence)
            OutlinedTextField(
                value = est.itemName,
                onValueChange = { onUpdate(est.copy(itemName = it)) },
                label = { Text("Item") },
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Grams", est.grams) { onUpdate(est.copy(grams = it)) }
            ScaleByGramsButton(
                currentGrams = est.grams,
                onApply = { newGrams -> onUpdate(scaleEstimate(est, newGrams)) },
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField("Calories", est.kcal) { onUpdate(est.copy(kcal = it)) }
            NumberField("Protein (g)", est.proteinG) { onUpdate(est.copy(proteinG = it)) }
            NumberField("Carbs (g)", est.carbsG) { onUpdate(est.copy(carbsG = it)) }
            NumberField("Fat (g)", est.fatG) { onUpdate(est.copy(fatG = it)) }
            NumberField("Fiber (g)", est.fiberG) { onUpdate(est.copy(fiberG = it)) }
            if (est.notes.isNotBlank()) {
                Text(
                    "Notes: ${est.notes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (est.detailPrompt.isNotBlank()) {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(
                        est.detailPrompt,
                        Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfidenceBadge(confidence: String) {
    val (label, color) = when (confidence.lowercase()) {
        "high" -> "High confidence" to MaterialTheme.colorScheme.primary
        "low" -> "Low confidence — please review" to MaterialTheme.colorScheme.error
        else -> "Medium confidence" to MaterialTheme.colorScheme.tertiary
    }
    Text(label, color = color, fontWeight = FontWeight.SemiBold)
}

private fun scaleEstimate(est: NutritionEstimate, newGrams: Double): NutritionEstimate {
    if (est.grams <= 0.0) return est.copy(grams = newGrams)
    val r = newGrams / est.grams
    return est.copy(
        grams = newGrams,
        kcal = est.kcal * r,
        proteinG = est.proteinG * r,
        carbsG = est.carbsG * r,
        fatG = est.fatG * r,
        fiberG = est.fiberG * r,
    )
}

@Composable
private fun NumberField(label: String, value: Double, onChange: (Double) -> Unit) {
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

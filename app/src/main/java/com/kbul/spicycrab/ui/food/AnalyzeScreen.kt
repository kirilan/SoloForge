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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kbul.spicycrab.R
import com.kbul.spicycrab.domain.nutrition.AnalysisAction
import com.kbul.spicycrab.domain.nutrition.NutritionEstimate
import com.kbul.spicycrab.domain.nutrition.UncertaintyReason
import com.kbul.spicycrab.domain.nutrition.hasValidNutrition
import com.kbul.spicycrab.domain.nutrition.routedAction
import com.kbul.spicycrab.domain.nutrition.strongerModelCanHelp
import java.io.File

@Composable
fun AnalyzeScreen(
    imageFile: File?,
    state: AnalyzeState,
    onCommentChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    /** Null when the chosen configuration has no stronger model above it. */
    escalationModelId: String?,
    onRetryStronger: () -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onEstimateUpdate: (NutritionEstimate) -> Unit,
) {
    val textOnly = imageFile == null
    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(if (textOnly) R.string.analyze_describe_title else R.string.analyze_review_title), style = MaterialTheme.typography.headlineMedium)
        if (imageFile != null) {
            AsyncImage(
                model = imageFile,
                contentDescription = stringResource(R.string.analyze_captured_cd),
                modifier = Modifier.fillMaxWidth().height(220.dp),
            )
        }
        OutlinedTextField(
            value = state.comment,
            onValueChange = onCommentChange,
            label = { Text(stringResource(if (textOnly) R.string.analyze_description else R.string.analyze_comment_optional)) },
            placeholder = {
                Text(stringResource(if (textOnly) R.string.analyze_placeholder_text else R.string.analyze_placeholder_photo))
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        if (state.estimate == null && !state.isLoading) {
            Button(
                onClick = onAnalyze,
                enabled = !textOnly || state.comment.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.analyze_button)) }
        }
        if (state.estimate != null && !state.isLoading) {
            OutlinedButton(
                onClick = onAnalyze,
                enabled = !textOnly || state.comment.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(if (textOnly) R.string.analyze_reanalyze_description else R.string.analyze_reanalyze_comment)) }
        }
        if (state.isLoading) {
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(0.dp))
                Text("  " + stringResource(R.string.analyze_analyzing), style = MaterialTheme.typography.bodyLarge)
            }
        }
        state.error?.let {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
            }
        }
        state.estimate?.let { est ->
            if (!state.isLoading && est.routedAction(hasImage = !textOnly) != AnalysisAction.ACCEPT) {
                UncertaintyCard(est, textOnly, escalationModelId, onRetryStronger)
            }
            EstimateForm(est, onEstimateUpdate)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.analyze_discard)) }
                Button(
                    onClick = onSave,
                    enabled = est.hasValidNutrition(),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.common_save)) }
            }
        }
        if (state.estimate == null && !state.isLoading) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.analyze_discard)) }
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
                label = { Text(stringResource(R.string.label_item)) },
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(stringResource(R.string.label_grams), est.grams) { onUpdate(est.copy(grams = it)) }
            ScaleByGramsButton(
                currentGrams = est.grams,
                onApply = { newGrams -> onUpdate(scaleEstimate(est, newGrams)) },
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(stringResource(R.string.label_calories), est.kcal) { onUpdate(est.copy(kcal = it)) }
            NumberField(stringResource(R.string.label_protein_g), est.proteinG) { onUpdate(est.copy(proteinG = it)) }
            NumberField(stringResource(R.string.label_carbs_g), est.carbsG) { onUpdate(est.copy(carbsG = it)) }
            NumberField(stringResource(R.string.label_fat_g), est.fatG) { onUpdate(est.copy(fatG = it)) }
            NumberField(stringResource(R.string.label_fiber_g), est.fiberG) { onUpdate(est.copy(fiberG = it)) }
            if (est.notes.isNotBlank()) {
                Text(
                    stringResource(R.string.analyze_notes, est.notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UncertaintyCard(
    est: NutritionEstimate,
    textOnly: Boolean,
    escalationModelId: String?,
    onRetryStronger: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.analysis_uncertain_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val hints = est.uncertaintyReasons.map { stringResource(it.hintRes(textOnly)) }
                .ifEmpty { listOf(stringResource(R.string.analysis_detail_prompt)) }
            hints.forEach {
                Text("• $it", style = MaterialTheme.typography.bodyMedium)
            }
            // "Add details" is always available; the stronger-model retry only exists when the
            // chosen configuration actually has something above it. Offering a sideways move
            // would be worse than offering nothing.
            if (escalationModelId == null) {
                Text(
                    stringResource(R.string.analysis_retry_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (!est.strongerModelCanHelp()) {
                    Text(
                        stringResource(R.string.analysis_retry_wont_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(
                    onClick = onRetryStronger,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { Text(stringResource(R.string.analysis_retry_stronger)) }
                Text(
                    stringResource(R.string.analysis_retry_disclosure, escalationModelId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun UncertaintyReason.hintRes(textOnly: Boolean): Int = when (this) {
    UncertaintyReason.IMAGE_QUALITY ->
        if (textOnly) R.string.analysis_hint_identity else R.string.analysis_hint_image_quality
    UncertaintyReason.IDENTITY_AMBIGUOUS -> R.string.analysis_hint_identity
    UncertaintyReason.PORTION_UNKNOWN -> R.string.analysis_hint_portion
    UncertaintyReason.PREPARATION_UNKNOWN -> R.string.analysis_hint_preparation
    UncertaintyReason.HIDDEN_INGREDIENTS -> R.string.analysis_hint_hidden
}

@Composable
private fun ConfidenceBadge(confidence: String) {
    val (labelRes, color) = when (confidence.lowercase()) {
        "high" -> R.string.confidence_high to MaterialTheme.colorScheme.primary
        "low" -> R.string.confidence_low to MaterialTheme.colorScheme.error
        else -> R.string.confidence_medium to MaterialTheme.colorScheme.tertiary
    }
    Text(stringResource(labelRes), color = color, fontWeight = FontWeight.SemiBold)
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

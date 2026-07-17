package com.kbul.spicycrab.ui.workout

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditWorkoutSheet(
    session: WorkoutSession,
    onSave: (WorkoutSession) -> Unit,
    onDelete: (WorkoutSession) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember(session.id) { mutableStateOf(session) }
    var error by remember(session.id) { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.workout_edit_title), style = MaterialTheme.typography.headlineMedium)

            MinutesField(
                stringResource(R.string.workout_total_minutes), draft.totalSeconds,
                onChange = { draft = draft.copy(totalSeconds = it) },
            )
            MinutesField(
                stringResource(R.string.workout_exercise_minutes), draft.exerciseSeconds,
                onChange = { draft = draft.copy(exerciseSeconds = it) },
            )
            MinutesField(
                stringResource(R.string.workout_rest_minutes), draft.restSeconds,
                onChange = { draft = draft.copy(restSeconds = it) },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { draft = draft.copy(notes = it) },
                label = { Text(stringResource(R.string.label_notes)) },
                modifier = Modifier.fillMaxWidth(),
            )

            error?.let {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(it, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.common_cancel)) }
                val errNonNegative = stringResource(R.string.workout_error_nonnegative)
                Button(
                    onClick = {
                        if (draft.totalSeconds < 0 || draft.exerciseSeconds < 0 || draft.restSeconds < 0) {
                            error = errNonNegative
                        } else onSave(draft)
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.common_save)) }
            }
            TextButton(
                onClick = { onDelete(session) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.workout_delete), color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun MinutesField(label: String, seconds: Long, onChange: (Long) -> Unit) {
    var input by remember(seconds) { mutableStateOf(formatMinutes(seconds / 60.0)) }
    OutlinedTextField(
        value = input,
        onValueChange = { txt ->
            input = txt.filter { it.isDigit() || it == '.' || it == ',' }
            val minutes = input.replace(',', '.').toDoubleOrNull() ?: 0.0
            onChange((minutes * 60.0).roundToLong())
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun formatMinutes(minutes: Double): String =
    "%.2f".format(minutes).trimEnd('0').trimEnd('.')

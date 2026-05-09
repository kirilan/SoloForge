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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.data.db.entities.WorkoutSession

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
            Text("Edit workout", style = MaterialTheme.typography.headlineMedium)

            NumField(
                "Total seconds", draft.totalSeconds,
                onChange = { draft = draft.copy(totalSeconds = it) },
            )
            NumField(
                "Exercise seconds", draft.exerciseSeconds,
                onChange = { draft = draft.copy(exerciseSeconds = it) },
            )
            NumField(
                "Rest seconds", draft.restSeconds,
                onChange = { draft = draft.copy(restSeconds = it) },
            )
            OutlinedTextField(
                value = draft.notes,
                onValueChange = { draft = draft.copy(notes = it) },
                label = { Text("Notes") },
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
                ) { Text("Cancel") }
                Button(
                    onClick = {
                        if (draft.totalSeconds < 0 || draft.exerciseSeconds < 0 || draft.restSeconds < 0) {
                            error = "Values must be non-negative."
                        } else onSave(draft)
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Save") }
            }
            TextButton(
                onClick = { onDelete(session) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete workout", color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun NumField(label: String, value: Long, onChange: (Long) -> Unit) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { txt -> onChange(txt.filter { it.isDigit() }.toLongOrNull() ?: 0L) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

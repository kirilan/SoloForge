package com.kbul.spicycrab.ui.fasting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.domain.fasting.FastingMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val ZONE: ZoneId = ZoneId.systemDefault()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditFastSheet(
    session: FastSession,
    onSave: (FastSession) -> Unit,
    onDelete: (FastSession) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var modeName by remember(session.id) { mutableStateOf(session.modeName) }
    var startText by remember(session.id) {
        mutableStateOf(formatEpoch(session.startEpoch))
    }
    var endText by remember(session.id) {
        mutableStateOf(session.endEpoch?.let { formatEpoch(it) } ?: "")
    }
    var error by remember(session.id) { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Edit fast", style = MaterialTheme.typography.headlineMedium)

            Text("Mode", style = MaterialTheme.typography.bodyMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FastingMode.entries) { mode ->
                    FilterChip(
                        selected = modeName == mode.name,
                        onClick = { modeName = mode.name },
                        label = { Text(mode.displayName) },
                    )
                }
            }

            OutlinedTextField(
                value = startText,
                onValueChange = { startText = it; error = null },
                label = { Text("Start (yyyy-MM-dd HH:mm)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = endText,
                onValueChange = { endText = it; error = null },
                label = { Text("End (blank = still active)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
                        val startEpoch = parseEpoch(startText)
                        val endEpoch = if (endText.isBlank()) null else parseEpoch(endText)
                        when {
                            startEpoch == null -> error = "Start time format invalid."
                            endEpoch != null && endEpoch < startEpoch ->
                                error = "End must be after start."
                            else -> onSave(
                                session.copy(
                                    modeName = modeName,
                                    startEpoch = startEpoch,
                                    endEpoch = endEpoch,
                                )
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Save") }
            }
            TextButton(
                onClick = { onDelete(session) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete fast", color = MaterialTheme.colorScheme.error) }
        }
    }
}

private fun formatEpoch(epoch: Long): String =
    FORMAT.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(epoch), ZONE))

private fun parseEpoch(text: String): Long? = runCatching {
    LocalDateTime.parse(text.trim(), FORMAT).atZone(ZONE).toInstant().toEpochMilli()
}.getOrNull()

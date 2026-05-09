package com.kbul.spicycrab.ui.fasting

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.domain.fasting.FastingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun FastingScreen(
    viewModel: FastingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user's choice respected silently */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val active = state.active
    val now = state.nowMillis

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            if (active != null) "Fasting" else "Ready when you are",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (active != null) {
            val elapsedSec = ((now - active.startEpoch) / 1000L).coerceAtLeast(0L)
            val progress = (elapsedSec.toFloat() / active.targetSeconds.toFloat()).coerceIn(0f, 1f)
            val remainingSec = (active.targetSeconds - elapsedSec).coerceAtLeast(0L)
            ProgressRing(
                progress = progress,
                centerLabel = if (remainingSec > 0) "Remaining" else "Goal reached",
                centerValue = formatHms(if (remainingSec > 0) remainingSec else elapsedSec),
            )
            Text(
                "Mode ${FastingMode.fromName(active.modeName).displayName} · ${formatHms(elapsedSec)} elapsed",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.openEdit(active) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text("Edit start") }
                Button(
                    onClick = { viewModel.stopFast() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text("End fast") }
            }
            OutlinedButton(
                onClick = { viewModel.cancelActive() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Cancel (delete this fast)") }
        } else {
            ProgressRing(
                progress = 0f,
                centerLabel = state.selectedMode.displayName,
                centerValue = "${state.selectedMode.fastHours}h",
            )
            Text("Pick a mode", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(FastingMode.entries) { mode ->
                    FilterChip(
                        selected = state.selectedMode == mode,
                        onClick = { viewModel.onModeSelected(mode) },
                        label = { Text(mode.displayName) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.startFast() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Start fast") }
        }

        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                "History",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Start),
            )
            state.history.take(20).forEach { session ->
                FastHistoryRow(session, onClick = { viewModel.openEdit(session) })
            }
        }
    }

    editing?.let { session ->
        EditFastSheet(
            session = session,
            onSave = viewModel::saveEdit,
            onDelete = viewModel::deleteSession,
            onDismiss = viewModel::dismissEdit,
        )
    }
}

@Composable
private fun FastHistoryRow(session: FastSession, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")
    val start = formatter.format(Instant.ofEpochMilli(session.startEpoch).atZone(zone))
    val durationSec = session.endEpoch?.let { (it - session.startEpoch) / 1000L } ?: 0L
    val mode = FastingMode.fromName(session.modeName)
    val statusLabel = if (session.completed) "Completed" else "Incomplete"
    val statusColor = if (session.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text("${mode.displayName} · ${formatHms(durationSec)}", style = MaterialTheme.typography.titleMedium)
            Text(start, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(statusLabel, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        }
    }
}

private fun formatHms(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

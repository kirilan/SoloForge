package com.kbul.spicycrab.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.domain.workout.ActiveWorkoutState
import com.kbul.spicycrab.domain.workout.WorkoutMode
import com.kbul.spicycrab.domain.workout.WorkoutPhase
import com.kbul.spicycrab.domain.workout.activeSeconds
import com.kbul.spicycrab.domain.workout.currentPhaseElapsedSeconds
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun WorkoutScreen(viewModel: WorkoutViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()

    val keepScreenOn = state.active != null && state.active!!.mode.requiresScreenOn
    val view = LocalView.current
    DisposableEffect(keepScreenOn) {
        view.keepScreenOn = keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Workout", style = MaterialTheme.typography.headlineMedium)

        val active = state.active
        if (active != null) {
            when (active.mode) {
                WorkoutMode.SIMPLE -> SimpleActiveCard(
                    state = active,
                    nowMs = state.nowMs,
                    onTogglePause = viewModel::togglePause,
                    onStop = viewModel::stop,
                )
                WorkoutMode.INTERVAL -> IntervalActiveCard(
                    state = active,
                    nowMs = state.nowMs,
                    onStop = viewModel::stop,
                )
                WorkoutMode.EXERCISE_REST -> ExerciseRestActiveCard(
                    state = active,
                    nowMs = state.nowMs,
                    onTogglePhase = viewModel::togglePhase,
                    onPause = viewModel::pause,
                    onStop = viewModel::stop,
                )
            }
        } else {
            ModeSelector(state.selectedMode, viewModel::onModeSelected)
            if (state.selectedMode == WorkoutMode.INTERVAL) {
                IntervalPicker(state.intervalMinutes, viewModel::onIntervalMinutesChange)
            }
            Button(
                onClick = viewModel::start,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Start workout") }
        }

        if (state.history.isNotEmpty()) {
            Text("History", style = MaterialTheme.typography.titleMedium)
            state.history.take(20).forEach { session ->
                WorkoutHistoryRow(session) { viewModel.openEdit(session) }
            }
        }
    }

    editing?.let { session ->
        EditWorkoutSheet(
            session = session,
            onSave = viewModel::saveEdit,
            onDelete = viewModel::deleteSession,
            onDismiss = viewModel::dismissEdit,
        )
    }
}

@Composable
private fun ModeSelector(selected: WorkoutMode, onSelect: (WorkoutMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Mode", style = MaterialTheme.typography.titleMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(WorkoutMode.entries) { mode ->
                FilterChip(
                    selected = selected == mode,
                    onClick = { onSelect(mode) },
                    label = { Text(mode.displayName) },
                )
            }
        }
        val description = when (selected) {
            WorkoutMode.SIMPLE -> "Times your session. Pause and resume any time."
            WorkoutMode.INTERVAL -> "Plays a beep every interval. Big counter resets each interval."
            WorkoutMode.EXERCISE_REST -> "Toggle between exercise and rest. Big counter resets at every toggle. Starts paused."
        }
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IntervalPicker(minutes: Double, onChange: (Double) -> Unit) {
    var input by remember(minutes) { mutableStateOf(formatMinutes(minutes)) }
    OutlinedTextField(
        value = input,
        onValueChange = { txt ->
            input = txt.filter { it.isDigit() || it == '.' || it == ',' }
            input.replace(',', '.').toDoubleOrNull()?.let(onChange)
        },
        label = { Text("Interval (minutes)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SimpleActiveCard(
    state: ActiveWorkoutState,
    nowMs: Long,
    onTogglePause: () -> Unit,
    onStop: () -> Unit,
) {
    val total = state.activeSeconds(nowMs)
    val isPaused = state.phase == WorkoutPhase.PAUSED
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Simple", style = MaterialTheme.typography.titleLarge)
            BigTimer(seconds = total)
            Text(
                if (isPaused) "Paused" else "Running",
                style = MaterialTheme.typography.titleMedium,
                color = if (isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
            Button(
                onClick = onTogglePause,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = if (isPaused)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                else ButtonDefaults.buttonColors(),
            ) { Text(if (isPaused) "Resume" else "Pause") }
            EndButton(onStop)
        }
    }
}

@Composable
private fun IntervalActiveCard(
    state: ActiveWorkoutState,
    nowMs: Long,
    onStop: () -> Unit,
) {
    val total = state.activeSeconds(nowMs)
    val intervalSec = state.intervalSeconds
    val intervalElapsed = if (intervalSec > 0) total % intervalSec else 0L
    val intervalsCompleted = if (intervalSec > 0) total / intervalSec else 0L

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Interval · ${formatMinutes(intervalSec / 60.0)} min", style = MaterialTheme.typography.titleLarge)
            BigTimer(seconds = intervalElapsed)
            Text("current interval", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(
                "Total: ${formatHms(total)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "${intervalsCompleted} interval${if (intervalsCompleted == 1L) "" else "s"} completed",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            EndButton(onStop)
        }
    }
}

@Composable
private fun ExerciseRestActiveCard(
    state: ActiveWorkoutState,
    nowMs: Long,
    onTogglePhase: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
) {
    val phaseElapsed = state.currentPhaseElapsedSeconds(nowMs)
    val total = state.activeSeconds(nowMs)
    val totalEx = state.accumulatedExerciseSeconds + if (state.phase == WorkoutPhase.EXERCISE) phaseElapsed else 0L
    val totalRest = state.accumulatedRestSeconds + if (state.phase == WorkoutPhase.REST) phaseElapsed else 0L

    val phaseColor = when (state.phase) {
        WorkoutPhase.EXERCISE -> MaterialTheme.colorScheme.primary
        WorkoutPhase.REST -> MaterialTheme.colorScheme.tertiary
        WorkoutPhase.PAUSED -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onPhaseColor = when (state.phase) {
        WorkoutPhase.EXERCISE -> MaterialTheme.colorScheme.onPrimary
        WorkoutPhase.REST -> MaterialTheme.colorScheme.onTertiary
        WorkoutPhase.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val phaseLabel = when (state.phase) {
        WorkoutPhase.EXERCISE -> "WORKING"
        WorkoutPhase.REST -> "RESTING"
        WorkoutPhase.PAUSED -> "PAUSED"
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = phaseColor),
    ) {
        Column(
            Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                phaseLabel,
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = 4.sp),
                color = onPhaseColor,
            )
            BigTimer(seconds = phaseElapsed, color = onPhaseColor)
            Text(
                "current phase",
                style = MaterialTheme.typography.bodyMedium,
                color = onPhaseColor.copy(alpha = 0.85f),
            )
        }
    }

    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        StatBlock("Exercise", totalEx)
        StatBlock("Rest", totalRest)
        StatBlock("Total", total)
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onTogglePhase,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) {
        val label = when (state.phase) {
            WorkoutPhase.EXERCISE -> "Switch to rest"
            WorkoutPhase.REST -> "Switch to exercise"
            WorkoutPhase.PAUSED -> "Begin exercise"
        }
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
    if (state.phase != WorkoutPhase.PAUSED) {
        OutlinedButton(
            onClick = onPause,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Text("Pause") }
    }
    EndButton(onStop)
}

@Composable
private fun StatBlock(label: String, seconds: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatHms(seconds), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun BigTimer(seconds: Long, color: Color = Color.Unspecified) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            formatHms(seconds),
            style = MaterialTheme.typography.displayLarge,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        )
    }
}

@Composable
private fun EndButton(onStop: () -> Unit) {
    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) { Text("End workout") }
}

@Composable
private fun WorkoutHistoryRow(session: WorkoutSession, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")
    val ts = formatter.format(Instant.ofEpochMilli(session.startEpoch).atZone(zone))
    val mode = WorkoutMode.fromName(session.modeName)
    val edited = session.lastModifiedEpoch > session.startEpoch + 1500
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${mode.displayName} · ${formatHms(session.totalSeconds)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                if (edited) "$ts · edited" else ts,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (mode == WorkoutMode.EXERCISE_REST) {
                Text(
                    "Exercise ${formatHms(session.exerciseSeconds)} · Rest ${formatHms(session.restSeconds)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (mode == WorkoutMode.INTERVAL && session.intervalSeconds > 0) {
                Text(
                    "Interval: ${formatMinutes(session.intervalSeconds / 60.0)} min",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (session.notes.isNotBlank()) {
                Text("“${session.notes}”", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}

private fun formatHms(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatMinutes(minutes: Double): String =
    "%.2f".format(minutes).trimEnd('0').trimEnd('.')

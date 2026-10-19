package com.kbul.spicycrab.ui.fasting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kbul.spicycrab.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.domain.fasting.FastingMode
import com.kbul.spicycrab.ui.common.formatHms
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import kotlin.math.roundToInt

@Composable
fun FastingScreen(
    viewModel: FastingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val active = state.active
    val now = state.nowMillis

    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(if (active != null) R.string.fasting_title else R.string.fasting_ready),
            style = MaterialTheme.typography.headlineMedium,
        )

        if (active != null) {
            val elapsedSec = ((now - active.startEpoch) / 1000L).coerceAtLeast(0L)
            val progress = (elapsedSec.toFloat() / active.targetSeconds.toFloat()).coerceIn(0f, 1f)
            val remainingSec = (active.targetSeconds - elapsedSec).coerceAtLeast(0L)
            ProgressRing(
                progress = progress,
                centerLabel = stringResource(if (remainingSec > 0) R.string.fasting_remaining else R.string.fasting_goal_reached),
                centerValue = formatHms(if (remainingSec > 0) remainingSec else elapsedSec),
            )
            Text(
                stringResource(
                    R.string.fasting_mode_elapsed,
                    FastingMode.label(active.modeName, active.targetSeconds),
                    formatHms(elapsedSec),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (active.targetSeconds > 86_400L) {
                val totalDays = ((active.targetSeconds + 86_399L) / 86_400L).toInt()
                val day = (elapsedSec / 86_400L + 1).toInt()
                Text(
                    stringResource(R.string.fasting_day_of, day, totalDays),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.openEdit(active) },
                    modifier = Modifier.weight(1f).height(56.dp),
                ) { Text(stringResource(R.string.fasting_edit_start)) }
                Button(
                    onClick = { viewModel.stopFast() },
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) { Text(stringResource(R.string.fasting_end)) }
            }
            OutlinedButton(
                onClick = { viewModel.cancelActive() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text(stringResource(R.string.fasting_cancel_delete)) }
        } else {
            val selected = state.selectedMode
            val targetHours = selected.targetSeconds(state.customFastHours) / 3600
            ProgressRing(
                progress = 0f,
                centerLabel = if (selected == FastingMode.CUSTOM) stringResource(R.string.fasting_mode_custom) else selected.displayName,
                centerValue = "${targetHours}h",
            )
            Text(stringResource(R.string.fasting_pick_mode), style = MaterialTheme.typography.titleMedium)
            FastingModeChips(selected, state.customFastHours, viewModel::onModeSelected)
            if (selected == FastingMode.CUSTOM) {
                CustomHoursPicker(state.customFastHours, viewModel::setCustomFastHours)
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.startFast() },
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(stringResource(R.string.fasting_start)) }
        }

        if (active != null || state.weekFastedSeconds.any { it > 0 }) {
            WeekStrip(state.weekStart, state.weekFastedSeconds, today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate())
        }

        if (state.history.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.common_history),
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
            activeSessionId = state.active?.id,
            onSave = viewModel::saveEdit,
            onDelete = viewModel::deleteSession,
            onDismiss = viewModel::dismissEdit,
        )
    }
}

/** Mode picker shared by the Fasting screen, the Home tile, and the edit sheet. */
@Composable
internal fun FastingModeChips(
    selected: FastingMode,
    customHours: Int,
    onModeSelected: (FastingMode) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        FastingMode.entries.forEach { mode ->
            val label = if (mode == FastingMode.CUSTOM) {
                stringResource(R.string.fasting_mode_custom_hours, customHours)
            } else {
                mode.displayName
            }
            FilterChip(
                selected = selected == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                leadingIcon = if (selected == mode) {
                    { Icon(Icons.Default.Check, contentDescription = null) }
                } else null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun CustomHoursPicker(hours: Int, onChange: (Int) -> Unit) {
    var dragging by remember(hours) { mutableFloatStateOf(hours.toFloat()) }
    val shown = dragging.roundToInt()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(hours - 1) }, enabled = hours > 1) {
                Icon(Icons.Default.Remove, contentDescription = stringResource(R.string.fasting_custom_less))
            }
            Text(
                stringResource(R.string.fasting_custom_target, shown, shown / 24, shown % 24),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { onChange(hours + 1) }, enabled = hours < FastingMode.MAX_CUSTOM_HOURS) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.fasting_custom_more))
            }
        }
        Slider(
            value = dragging,
            onValueChange = { dragging = it },
            onValueChangeFinished = { onChange(dragging.roundToInt()) },
            // ponytail: no `steps` — 167 tick marks smear the track; rounding gives whole hours anyway.
            valueRange = 1f..FastingMode.MAX_CUSTOM_HOURS.toFloat(),
        )
        if (shown > 72) {
            Text(
                stringResource(R.string.fasting_extended_warning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun WeekStrip(weekStart: LocalDate, fastedSeconds: List<Long>, today: LocalDate) {
    val locale = LocalLocale.current.platformLocale
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.fasting_this_week), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                fastedSeconds.forEachIndexed { i, seconds ->
                    val date = weekStart.plusDays(i.toLong())
                    val isToday = date == today
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isToday) FontWeight.Bold else null,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { seconds / 86_400f },
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 4.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        Text(
                            if (date.isAfter(today)) "" else stringResource(R.string.fasting_week_hours, (seconds / 3600).toInt()),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FastHistoryRow(session: FastSession, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val formatter = DateTimeFormatter.ofPattern("MMM d · HH:mm")
    val start = formatter.format(Instant.ofEpochMilli(session.startEpoch).atZone(zone))
    val durationSec = session.endEpoch?.let { (it - session.startEpoch) / 1000L } ?: 0L
    val statusLabel = stringResource(if (session.completed) R.string.fasting_completed else R.string.fasting_incomplete)
    val statusColor = if (session.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "${FastingMode.label(session.modeName, session.targetSeconds)} · ${formatHms(durationSec)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(start, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(statusLabel, style = MaterialTheme.typography.bodyMedium, color = statusColor)
        }
    }
}

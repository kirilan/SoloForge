package com.kbul.spicycrab.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.prefs.NutritionGoals
import com.kbul.spicycrab.domain.fasting.FastingMode
import com.kbul.spicycrab.domain.workout.WorkoutMode
import com.kbul.spicycrab.ui.fasting.ProgressRing
import com.kbul.spicycrab.ui.nav.TopLevelDest
import com.kbul.spicycrab.ui.theme.GoalNear
import com.kbul.spicycrab.ui.theme.GoalOver
import com.kbul.spicycrab.ui.theme.GoalUnder
import com.kbul.spicycrab.ui.weight.ChartPoint
import com.kbul.spicycrab.ui.weight.WeightChart
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen(
    onNavigate: (TopLevelDest) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineMedium)

        ProgressCalendarTile(
            state = state,
            onPreviousDay = viewModel::previousCalendarDay,
            onNextDay = viewModel::nextCalendarDay,
            onPreviousMonth = viewModel::previousCalendarMonth,
            onNextMonth = viewModel::nextCalendarMonth,
            onDaySelected = viewModel::selectCalendarDay,
            toDisplayWeight = viewModel::toDisplayWeight,
        )

        if (state.showFasting) {
            FastingTile(
                state = state,
                onModeSelected = viewModel::onModeSelected,
                onStart = viewModel::startFast,
                onStop = viewModel::stopFast,
                onClick = { onNavigate(TopLevelDest.Fasting) },
            )
        }

        if (state.showFood) {
            NutritionTile(
                entries = state.todayEntries,
                goals = state.goals,
                workoutBonusKcal = state.workoutBonusKcal,
                onClick = { onNavigate(TopLevelDest.Food) },
            )
        }

        if (state.showWorkout) {
            WorkoutTile(
                todaySeconds = state.todayWorkoutSeconds,
                onClick = { onNavigate(TopLevelDest.Workout) },
            )
        }

        if (state.showWeight) {
            WeightTile(
                entries = state.weightEntries,
                useKg = state.useKg,
                toDisplay = viewModel::toDisplayWeight,
                onClick = { onNavigate(TopLevelDest.Weight) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProgressCalendarTile(
    state: HomeUiState,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDaySelected: (LocalDate) -> Unit,
    toDisplayWeight: (Double) -> Double,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Progress calendar", style = MaterialTheme.typography.titleLarge)
            CompactDaySelector(
                day = state.selectedDay,
                selectedDate = state.selectedCalendarDate,
                onPreviousDay = onPreviousDay,
                onNextDay = onNextDay,
                onExpand = { expanded = true },
            )

            if (expanded) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(onClick = onPreviousMonth, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text("<")
                    }
                    Text(
                        state.calendarMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                            " ${state.calendarMonth.year}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    OutlinedButton(onClick = onNextMonth, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Text(">")
                    }
                }

                CalendarLegend()

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DayOfWeek.entries.forEach { day ->
                        Text(
                            day.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(2),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.calendarDays.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        week.forEach { day ->
                            CalendarDayCell(
                                day = day,
                                selected = day.date == state.selectedCalendarDate,
                                onClick = {
                                    onDaySelected(day.date)
                                    expanded = false
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            SelectedDayDetails(
                day = state.selectedDay,
                useKg = state.useKg,
                toDisplayWeight = toDisplayWeight,
            )
        }
    }
}

@Composable
private fun CompactDaySelector(
    day: CalendarDaySummary?,
    selectedDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onExpand: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(onClick = onPreviousDay, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text("<")
        }
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                .clickable(onClick = onExpand)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                    " ${selectedDate.dayOfMonth}, ${selectedDate.year}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (day != null) {
                CompactMarkers(day)
            }
        }
        OutlinedButton(onClick = onNextDay, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text(">")
        }
    }
}

@Composable
private fun CompactMarkers(day: CalendarDaySummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${day.kcal.toInt()} / ${day.calorieBudget} kcal", style = MaterialTheme.typography.labelMedium)
        if (day.meals.isNotEmpty()) Marker(MaterialTheme.colorScheme.primary)
        if (day.fasts.isNotEmpty()) Marker(MaterialTheme.colorScheme.tertiary)
        if (day.workouts.isNotEmpty()) Marker(MaterialTheme.colorScheme.secondary)
        if (day.weights.isNotEmpty()) Marker(MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun CalendarLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Food", MaterialTheme.colorScheme.primary)
        LegendItem("Fast", MaterialTheme.colorScheme.tertiary)
        LegendItem("Workout", MaterialTheme.colorScheme.secondary)
        LegendItem("Weight", MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CalendarDayCell(
    day: CalendarDaySummary,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        day.isToday -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val contentColor = if (day.inMonth) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    }

    Column(
        modifier
            .height(82.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = if (selected || day.isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Box(
            Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                Modifier.fillMaxWidth(day.calorieProgress.coerceAtMost(1f)).fillMaxSize()
                    .background(goalColor(day.kcal, day.calorieBudget.toDouble()))
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            if (day.meals.isNotEmpty()) Marker(MaterialTheme.colorScheme.primary)
            if (day.fasts.isNotEmpty()) Marker(MaterialTheme.colorScheme.tertiary)
            if (day.workouts.isNotEmpty()) Marker(MaterialTheme.colorScheme.secondary)
            if (day.weights.isNotEmpty()) Marker(MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun Marker(color: Color) {
    Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
}

@Composable
private fun SelectedDayDetails(
    day: CalendarDaySummary?,
    useKg: Boolean,
    toDisplayWeight: (Double) -> Double,
) {
    if (day == null) return

    val unit = if (useKg) "kg" else "lb"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                ", ${day.date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.date.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
        )

        val workoutBonus = day.calorieBudget - day.baseCalorieGoal
        Text(
            "${day.kcal.toInt()} / ${day.calorieBudget} kcal" +
                if (workoutBonus > 0) " (+$workoutBonus from training)" else "",
            style = MaterialTheme.typography.bodyLarge,
            color = goalColor(day.kcal, day.calorieBudget.toDouble()),
        )
        GoalBar(day.kcal, day.calorieBudget.toDouble())

        if (!day.hasData) {
            Text("No events logged for this day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return
        }

        day.fasts.forEach { fast ->
            val mode = FastingMode.fromName(fast.modeName)
            val duration = ((fast.endEpoch ?: System.currentTimeMillis()) - fast.startEpoch).coerceAtLeast(0L) / 1000L
            EventLine(
                label = "Fast",
                value = "${mode.displayName} - ${formatDuration(duration)}" +
                    if (fast.completed) " completed" else " active",
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        day.meals.forEach { meal ->
            EventLine(
                label = "Meal",
                value = "${meal.itemName} - ${meal.kcal.toInt()} kcal",
                color = MaterialTheme.colorScheme.primary,
            )
        }
        day.workouts.forEach { workout ->
            EventLine(
                label = "Workout",
                value = "${WorkoutMode.fromName(workout.modeName).displayName} - ${formatDuration(workout.totalSeconds)}",
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        day.weights.forEach { weight ->
            EventLine(
                label = "Weight",
                value = "${"%.1f".format(toDisplayWeight(weight.weightKg))} $unit",
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun EventLine(label: String, value: String, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun FastingTile(
    state: HomeUiState,
    onModeSelected: (FastingMode) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Fasting", style = MaterialTheme.typography.titleLarge)
                if (state.streak > 0) {
                    AssistChip(
                        onClick = onClick,
                        label = { Text("${state.streak}-day streak") },
                        leadingIcon = { Text("🔥") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    )
                }
            }

            val active = state.activeFast
            val recent = state.mostRecentFast
            val now = state.nowMs

            when {
                active != null -> ActiveFastContent(active, now, onStop)
                recent != null && isInEatingWindow(recent, now) -> EatingWindowContent(recent, now, state.selectedMode, onModeSelected, onStart)
                else -> IdleContent(state.selectedMode, onModeSelected, onStart)
            }
        }
    }
}

@Composable
private fun ActiveFastContent(active: com.kbul.spicycrab.data.db.entities.FastSession, now: Long, onStop: () -> Unit) {
    val elapsedSec = ((now - active.startEpoch) / 1000L).coerceAtLeast(0L)
    val progress = (elapsedSec.toFloat() / active.targetSeconds.toFloat()).coerceIn(0f, 1f)
    val remaining = (active.targetSeconds - elapsedSec).coerceAtLeast(0L)
    val mode = FastingMode.fromName(active.modeName)

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ProgressRing(
            progress = progress,
            centerLabel = if (remaining > 0) "Remaining" else "Done",
            centerValue = formatHms(if (remaining > 0) remaining else elapsedSec),
        )
    }
    Text("Mode ${mode.displayName} · ${formatHms(elapsedSec)} elapsed", style = MaterialTheme.typography.bodyLarge)
    Button(
        onClick = onStop,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) { Text("End fast") }
}

@Composable
private fun EatingWindowContent(
    recent: com.kbul.spicycrab.data.db.entities.FastSession,
    now: Long,
    selected: FastingMode,
    onModeSelected: (FastingMode) -> Unit,
    onStart: () -> Unit,
) {
    val end = recent.endEpoch ?: return
    val windowMs = recent.eatingWindowSeconds * 1000L
    val elapsedMs = (now - end).coerceAtLeast(0L)
    val remainingMs = (windowMs - elapsedMs).coerceAtLeast(0L)
    val progress = (elapsedMs.toFloat() / windowMs.toFloat()).coerceIn(0f, 1f)
    val remainSec = remainingMs / 1000L

    Text("Eating window", fontWeight = FontWeight.SemiBold)
    LinearProgressIndicator(
        progress = { progress },
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)),
    )
    Text(
        "${formatHms(remainSec)} until window closes",
        style = MaterialTheme.typography.bodyLarge,
    )
    ModeChips(selected, onModeSelected)
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text("Start ${selected.displayName} fast") }
}

@Composable
private fun IdleContent(
    selected: FastingMode,
    onModeSelected: (FastingMode) -> Unit,
    onStart: () -> Unit,
) {
    Text("Ready when you are.", style = MaterialTheme.typography.bodyLarge)
    ModeChips(selected, onModeSelected)
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text("Start ${selected.displayName} fast") }
}

@Composable
private fun ModeChips(selected: FastingMode, onModeSelected: (FastingMode) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(FastingMode.entries) { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onModeSelected(mode) },
                label = { Text(mode.displayName) },
            )
        }
    }
}

@Composable
private fun NutritionTile(
    entries: List<FoodEntry>,
    goals: NutritionGoals,
    workoutBonusKcal: Int,
    onClick: () -> Unit,
) {
    val kcal = entries.sumOf { it.kcal }
    val protein = entries.sumOf { it.proteinG }
    val carbs = entries.sumOf { it.carbsG }
    val fat = entries.sumOf { it.fatG }
    val adjustedKcalGoal = goals.kcal + workoutBonusKcal

    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Today's nutrition", style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${kcal.toInt()}",
                    style = MaterialTheme.typography.displayMedium,
                    color = goalColor(kcal, adjustedKcalGoal.toDouble()),
                )
                Text(
                    " / $adjustedKcalGoal kcal",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (workoutBonusKcal > 0) {
                Text(
                    "+$workoutBonusKcal kcal from training",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            GoalBar(kcal, adjustedKcalGoal.toDouble())

            Spacer(Modifier.height(4.dp))
            MacroLine("Protein", protein, goals.proteinG.toDouble())
            MacroLine("Carbs", carbs, goals.carbsG.toDouble())
            MacroLine("Fat", fat, goals.fatG.toDouble())

            Text(
                "${entries.size} ${if (entries.size == 1) "meal" else "meals"} tracked today",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GoalBar(current: Double, goal: Double) {
    val progress = if (goal <= 0) 0f else (current / goal).toFloat().coerceIn(0f, 1.5f)
    val color = goalColor(current, goal)
    Box(
        Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            Modifier.fillMaxWidth(progress.coerceAtMost(1f)).fillMaxSize().background(color)
        )
    }
}

@Composable
private fun MacroLine(label: String, current: Double, goal: Double) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("${current.toInt()} / ${goal.toInt()} g", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        GoalBar(current, goal)
    }
}

@Composable
private fun WeightTile(
    entries: List<WeightEntry>,
    useKg: Boolean,
    toDisplay: (Double) -> Double,
    onClick: () -> Unit,
) {
    val unit = if (useKg) "kg" else "lb"
    val latest = entries.firstOrNull()
    val previous = entries.getOrNull(1)
    val cutoff = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
    val recentPoints = entries
        .filter { it.timestampEpoch >= cutoff }
        .map { ChartPoint(it.timestampEpoch, toDisplay(it.weightKg)) }

    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Weight", style = MaterialTheme.typography.titleLarge)
            if (latest == null) {
                Text("No weight logged yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val cur = toDisplay(latest.weightKg)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${"%.1f".format(cur)}", style = MaterialTheme.typography.displayMedium)
                    Text(" $unit", style = MaterialTheme.typography.titleMedium)
                }
                if (previous != null) {
                    val delta = cur - toDisplay(previous.weightKg)
                    val sign = if (delta >= 0) "+" else ""
                    val color = when {
                        delta > 0 -> MaterialTheme.colorScheme.error
                        delta < 0 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text("$sign${"%.1f".format(delta)} $unit since last", color = color)
                }
                if (recentPoints.size >= 2) {
                    WeightChart(points = recentPoints, unitLabel = unit)
                }
            }
        }
    }
}

@Composable
private fun WorkoutTile(todaySeconds: Long, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workout", style = MaterialTheme.typography.titleLarge)
            if (todaySeconds <= 0) {
                Text(
                    "No workout logged today.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val h = todaySeconds / 3600
                val m = (todaySeconds % 3600) / 60
                val label = if (h > 0) "${h}h ${m}m" else "${m}m"
                Text(label, style = MaterialTheme.typography.displayMedium)
                Text("logged today", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun goalColor(current: Double, goal: Double): Color {
    val ratio = if (goal <= 0) 0.0 else current / goal
    return when {
        ratio >= 1.0 -> GoalOver
        ratio >= 0.9 -> GoalNear
        else -> GoalUnder
    }
}

private fun isInEatingWindow(session: com.kbul.spicycrab.data.db.entities.FastSession, now: Long): Boolean {
    val end = session.endEpoch ?: return false
    return now in end..(end + session.eatingWindowSeconds * 1000L)
}

private fun formatHms(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

private fun formatDuration(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return when {
        seconds < 60 -> "${seconds}s"
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        else -> "${m}m"
    }
}

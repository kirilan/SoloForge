package com.kbul.spicycrab.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.data.prefs.NutritionGoals
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.fasting.FastingMode
import com.kbul.spicycrab.domain.fasting.FastingRepository
import com.kbul.spicycrab.domain.fasting.StreakCalculator
import com.kbul.spicycrab.domain.nutrition.FoodRepository
import com.kbul.spicycrab.domain.weight.WeightRepository
import com.kbul.spicycrab.domain.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class CalendarDaySummary(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
    val kcal: Double,
    val baseCalorieGoal: Int,
    val calorieBudget: Int,
    val meals: List<FoodEntry>,
    val fasts: List<FastSession>,
    val workouts: List<WorkoutSession>,
    val weights: List<WeightEntry>,
) {
    val workoutSeconds: Long = workouts.sumOf { it.totalSeconds }
    val hasData: Boolean = meals.isNotEmpty() || fasts.isNotEmpty() || workouts.isNotEmpty() || weights.isNotEmpty()
    val calorieProgress: Float =
        if (calorieBudget <= 0) 0f else (kcal / calorieBudget.toDouble()).toFloat().coerceIn(0f, 1.5f)
}

data class HomeUiState(
    val nowMs: Long = System.currentTimeMillis(),
    val activeFast: FastSession? = null,
    val mostRecentFast: FastSession? = null,
    val streak: Int = 0,
    val selectedMode: FastingMode = FastingMode.SIXTEEN_EIGHT,
    val todayEntries: List<FoodEntry> = emptyList(),
    val goals: NutritionGoals = NutritionGoals(2000, 150, 220, 65, 30),
    val weightEntries: List<WeightEntry> = emptyList(),
    val useKg: Boolean = true,
    val workoutBonusKcal: Int = 0,
    val todayWorkoutSeconds: Long = 0L,
    val showFasting: Boolean = true,
    val showFood: Boolean = true,
    val showWeight: Boolean = true,
    val showWorkout: Boolean = true,
    val calendarMonth: YearMonth = YearMonth.now(),
    val selectedCalendarDate: LocalDate = LocalDate.now(),
    val calendarDays: List<CalendarDaySummary> = emptyList(),
    val selectedDay: CalendarDaySummary? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val fastingRepository: FastingRepository,
    private val foodRepository: FoodRepository,
    private val weightRepository: WeightRepository,
    private val workoutRepository: WorkoutRepository,
    private val settings: SettingsRepo,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val selectedMode = MutableStateFlow(FastingMode.SIXTEEN_EIGHT)
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private var userPickedMode = false

    init {
        viewModelScope.launch {
            settings.settings.map { FastingMode.fromName(it.defaultFastingModeName) }.collect { mode ->
                if (!userPickedMode) selectedMode.value = mode
            }
        }
    }

    val state: StateFlow<HomeUiState> = combine(
        listOf(
            ticker,
            fastingRepository.observeAll(),
            foodRepository.observeAll(),
            weightRepository.observeAll(),
            workoutRepository.observeAll(),
            settings.settings,
            selectedMode,
            selectedMonth,
            selectedDate,
        )
    ) { values ->
        val now = values[0] as Long
        @Suppress("UNCHECKED_CAST") val fasts = values[1] as List<FastSession>
        @Suppress("UNCHECKED_CAST") val foods = values[2] as List<FoodEntry>
        @Suppress("UNCHECKED_CAST") val weights = values[3] as List<WeightEntry>
        @Suppress("UNCHECKED_CAST") val workouts = values[4] as List<WorkoutSession>
        val s = values[5] as com.kbul.spicycrab.data.prefs.AppSettings
        val mode = values[6] as FastingMode
        val month = values[7] as YearMonth
        val selectedDayDate = values[8] as LocalDate

        val zone = ZoneId.systemDefault()
        val todayDate = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val startOfDay = todayDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfDay = todayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = foods.filter { it.timestampEpoch >= startOfDay && it.timestampEpoch < endOfDay }
        val workoutsToday = workouts.filter { it.startEpoch >= startOfDay && it.startEpoch < endOfDay }
        val totalWorkoutSeconds = workoutsToday.sumOf { it.totalSeconds }
        val bonus = (250.0 * totalWorkoutSeconds / 3600.0).toInt()
        val calendarDays = buildCalendarDays(
            month = month,
            selectedToday = todayDate,
            now = now,
            zone = zone,
            foods = foods,
            fasts = fasts,
            workouts = workouts,
            weights = weights,
            baseCalorieGoal = s.goals.kcal,
        )

        HomeUiState(
            nowMs = now,
            activeFast = fasts.firstOrNull { it.endEpoch == null },
            mostRecentFast = fasts.firstOrNull { it.endEpoch != null && it.completed },
            streak = StreakCalculator.currentStreak(fasts.filter { it.completed }),
            selectedMode = mode,
            todayEntries = today,
            goals = s.goals,
            weightEntries = weights,
            useKg = s.weightUnitKg,
            workoutBonusKcal = bonus,
            todayWorkoutSeconds = totalWorkoutSeconds,
            showFasting = s.showFastingTab,
            showFood = s.showFoodTab,
            showWeight = s.showWeightTab,
            showWorkout = s.showWorkoutTab,
            calendarMonth = month,
            selectedCalendarDate = selectedDayDate,
            calendarDays = calendarDays,
            selectedDay = calendarDays.firstOrNull { it.date == selectedDayDate },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onModeSelected(mode: FastingMode) {
        userPickedMode = true
        selectedMode.value = mode
    }

    fun startFast() {
        viewModelScope.launch { fastingRepository.startFast(selectedMode.value) }
    }

    fun stopFast() {
        viewModelScope.launch {
            val active = state.value.activeFast ?: return@launch
            fastingRepository.stopFast(active)
        }
    }

    fun previousCalendarMonth() {
        val nextMonth = selectedMonth.value.minusMonths(1)
        selectedMonth.value = nextMonth
        selectedDate.value = nextMonth.atDay(1)
    }

    fun nextCalendarMonth() {
        val nextMonth = selectedMonth.value.plusMonths(1)
        selectedMonth.value = nextMonth
        selectedDate.value = nextMonth.atDay(1)
    }

    fun previousCalendarDay() {
        selectCalendarDay(selectedDate.value.minusDays(1))
    }

    fun nextCalendarDay() {
        selectCalendarDay(selectedDate.value.plusDays(1))
    }

    fun selectCalendarDay(date: LocalDate) {
        selectedDate.value = date
        selectedMonth.value = YearMonth.from(date)
    }

    fun toDisplayWeight(kg: Double): Double = weightRepository.toDisplayUnit(kg, state.value.useKg)
}

private fun buildCalendarDays(
    month: YearMonth,
    selectedToday: LocalDate,
    now: Long,
    zone: ZoneId,
    foods: List<FoodEntry>,
    fasts: List<FastSession>,
    workouts: List<WorkoutSession>,
    weights: List<WeightEntry>,
    baseCalorieGoal: Int,
): List<CalendarDaySummary> {
    val first = month.atDay(1)
    val leadingDays = first.dayOfWeek.value - 1
    val gridStart = first.minusDays(leadingDays.toLong())

    return (0 until 42).map { offset ->
        val date = gridStart.plusDays(offset.toLong())
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayWorkouts = workouts.filter { it.startEpoch >= start && it.startEpoch < end }
        val workoutBonus = (250.0 * dayWorkouts.sumOf { it.totalSeconds } / 3600.0).toInt()

        CalendarDaySummary(
            date = date,
            inMonth = YearMonth.from(date) == month,
            isToday = date == selectedToday,
            kcal = foods.filter { it.timestampEpoch >= start && it.timestampEpoch < end }.sumOf { it.kcal },
            baseCalorieGoal = baseCalorieGoal,
            calorieBudget = baseCalorieGoal + workoutBonus,
            meals = foods.filter { it.timestampEpoch >= start && it.timestampEpoch < end },
            fasts = fasts.filter { it.overlapsDay(start, end, now) },
            workouts = dayWorkouts,
            weights = weights.filter { it.timestampEpoch >= start && it.timestampEpoch < end },
        )
    }
}

private fun FastSession.overlapsDay(startOfDay: Long, endOfDay: Long, now: Long): Boolean {
    val fastEnd = endEpoch ?: now
    return startEpoch < endOfDay && fastEnd >= startOfDay
}

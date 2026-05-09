package com.kbul.spicycrab.ui.workout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.domain.workout.ActiveWorkoutState
import com.kbul.spicycrab.domain.workout.WorkoutMode
import com.kbul.spicycrab.domain.workout.WorkoutRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WorkoutUiState(
    val active: ActiveWorkoutState? = null,
    val nowMs: Long = System.currentTimeMillis(),
    val history: List<WorkoutSession> = emptyList(),
    val selectedMode: WorkoutMode = WorkoutMode.SIMPLE,
    val intervalMinutes: Int = 2,
)

@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val repository: WorkoutRepository,
) : ViewModel() {

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val selectedMode = MutableStateFlow(WorkoutMode.SIMPLE)
    private val intervalMinutes = MutableStateFlow(2)

    val uiState: StateFlow<WorkoutUiState> = combine(
        repository.observeActive(),
        repository.observeAll(),
        selectedMode,
        intervalMinutes,
        ticker,
    ) { active, all, mode, interval, now ->
        WorkoutUiState(
            active = active,
            nowMs = now,
            history = all.filter { it.endEpoch != null },
            selectedMode = mode,
            intervalMinutes = interval,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutUiState())

    private val _editing = MutableStateFlow<WorkoutSession?>(null)
    val editing: StateFlow<WorkoutSession?> = _editing.asStateFlow()

    fun onModeSelected(mode: WorkoutMode) { selectedMode.value = mode }
    fun onIntervalMinutesChange(minutes: Int) { intervalMinutes.value = minutes.coerceIn(1, 60) }

    fun start() {
        viewModelScope.launch {
            repository.start(
                mode = selectedMode.value,
                intervalSeconds = intervalMinutes.value * 60,
            )
        }
    }

    fun togglePhase() = repository.togglePhase()
    fun togglePause() = repository.togglePause()
    fun pause() = repository.pause()

    fun stop() {
        viewModelScope.launch { repository.stop() }
    }

    fun openEdit(session: WorkoutSession) { _editing.value = session }
    fun dismissEdit() { _editing.value = null }
    fun saveEdit(updated: WorkoutSession) {
        viewModelScope.launch {
            repository.update(updated)
            _editing.value = null
        }
    }
    fun deleteSession(session: WorkoutSession) {
        viewModelScope.launch {
            repository.delete(session)
            _editing.value = null
        }
    }
}

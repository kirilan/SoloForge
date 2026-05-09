package com.kbul.spicycrab.domain.workout

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ActiveWorkoutState(
    val sessionId: Long,
    val mode: WorkoutMode,
    val startEpoch: Long,
    val intervalSeconds: Int,
    val phase: WorkoutPhase,
    val phaseStartEpoch: Long,
    val accumulatedExerciseSeconds: Long,
    val accumulatedRestSeconds: Long,
)

fun ActiveWorkoutState.currentPhaseElapsedSeconds(nowMs: Long): Long {
    if (phase == WorkoutPhase.PAUSED) return 0L
    return ((nowMs - phaseStartEpoch) / 1000L).coerceAtLeast(0L)
}

fun ActiveWorkoutState.activeSeconds(nowMs: Long): Long {
    val phaseElapsed = currentPhaseElapsedSeconds(nowMs)
    val phaseAdds = when (phase) {
        WorkoutPhase.EXERCISE, WorkoutPhase.REST -> phaseElapsed
        WorkoutPhase.PAUSED -> 0L
    }
    return accumulatedExerciseSeconds + accumulatedRestSeconds + phaseAdds
}

@Singleton
class WorkoutStateHolder @Inject constructor() {
    private val _state = MutableStateFlow<ActiveWorkoutState?>(null)
    val state: StateFlow<ActiveWorkoutState?> = _state.asStateFlow()

    fun set(state: ActiveWorkoutState?) { _state.value = state }
    fun current(): ActiveWorkoutState? = _state.value
    fun update(transform: (ActiveWorkoutState) -> ActiveWorkoutState) {
        _state.value = _state.value?.let(transform)
    }
}

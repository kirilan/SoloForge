package com.kbul.spicycrab.domain.workout

import com.kbul.spicycrab.data.db.entities.WorkoutSession
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

/**
 * Wall-clock instant the notification chronometer counts up from to show the active time, or null
 * while paused — a chronometer cannot be stopped, so a paused workout must not use one at all.
 */
fun ActiveWorkoutState.chronometerBase(nowMs: Long): Long? =
    if (phase == WorkoutPhase.PAUSED) null else nowMs - activeSeconds(nowMs) * 1000L

fun WorkoutSession.toActiveWorkoutState(nowMs: Long = System.currentTimeMillis()): ActiveWorkoutState? {
    if (endEpoch != null) return null
    val mode = WorkoutMode.fromName(modeName)
    val phase = activePhaseName
        ?.let { stored -> WorkoutPhase.entries.firstOrNull { it.name == stored } }
        ?: if (mode == WorkoutMode.EXERCISE_REST) WorkoutPhase.PAUSED else WorkoutPhase.EXERCISE
    val persistedPhaseStart = phaseStartEpoch
        ?: if (phase == WorkoutPhase.PAUSED) nowMs else startEpoch
    return ActiveWorkoutState(
        sessionId = id,
        mode = mode,
        startEpoch = startEpoch,
        intervalSeconds = intervalSeconds,
        phase = phase,
        phaseStartEpoch = persistedPhaseStart,
        accumulatedExerciseSeconds = exerciseSeconds,
        accumulatedRestSeconds = restSeconds,
    )
}

fun WorkoutSession.effectiveTotalSeconds(nowMs: Long): Long =
    toActiveWorkoutState(nowMs)?.activeSeconds(nowMs) ?: totalSeconds

fun reconcileActiveWorkoutState(
    memory: ActiveWorkoutState?,
    database: WorkoutSession?,
    nowMs: Long = System.currentTimeMillis(),
): ActiveWorkoutState? {
    if (database == null) return null
    return memory?.takeIf { it.sessionId == database.id }
        ?: database.toActiveWorkoutState(nowMs)
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

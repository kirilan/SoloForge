package com.kbul.spicycrab.domain.workout

import com.kbul.spicycrab.data.db.entities.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutStateRecoveryTest {

    @Test
    fun pausedSimpleWorkoutRemainsPausedAfterRecovery() {
        val recovered = session(
            mode = WorkoutMode.SIMPLE,
            phase = WorkoutPhase.PAUSED,
            phaseStart = 5_000,
            exerciseSeconds = 120,
        ).toActiveWorkoutState(nowMs = 50_000)!!

        assertEquals(WorkoutPhase.PAUSED, recovered.phase)
        assertEquals(120L, recovered.activeSeconds(50_000))
    }

    @Test
    fun exerciseRestTotalsAndCurrentPhaseSurviveRecovery() {
        val recovered = session(
            mode = WorkoutMode.EXERCISE_REST,
            phase = WorkoutPhase.REST,
            phaseStart = 40_000,
            exerciseSeconds = 180,
            restSeconds = 30,
        ).toActiveWorkoutState(nowMs = 50_000)!!

        assertEquals(WorkoutPhase.REST, recovered.phase)
        assertEquals(220L, recovered.activeSeconds(50_000))
    }

    @Test
    fun completedWorkoutDoesNotRecoverAsActive() {
        assertNull(session(mode = WorkoutMode.SIMPLE).copy(endEpoch = 10_000).toActiveWorkoutState())
    }

    @Test
    fun missingDatabaseRowClearsStaleMemoryState() {
        val memory = session(mode = WorkoutMode.SIMPLE)
            .toActiveWorkoutState(nowMs = 5_000)

        assertNull(reconcileActiveWorkoutState(memory, database = null, nowMs = 5_000))
    }

    private fun session(
        mode: WorkoutMode,
        phase: WorkoutPhase? = null,
        phaseStart: Long? = null,
        exerciseSeconds: Long = 0,
        restSeconds: Long = 0,
    ) = WorkoutSession(
        id = 7,
        modeName = mode.name,
        startEpoch = 1_000,
        endEpoch = null,
        totalSeconds = 0,
        intervalSeconds = 0,
        exerciseSeconds = exerciseSeconds,
        restSeconds = restSeconds,
        notes = "",
        lastModifiedEpoch = 1_000,
        activePhaseName = phase?.name,
        phaseStartEpoch = phaseStart,
    )
}

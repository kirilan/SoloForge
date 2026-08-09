package com.kbul.spicycrab.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChronometerBaseTest {

    private fun state(phase: WorkoutPhase, phaseStart: Long, exercise: Long = 0L, rest: Long = 0L) =
        ActiveWorkoutState(
            sessionId = 1L,
            mode = WorkoutMode.EXERCISE_REST,
            startEpoch = phaseStart,
            intervalSeconds = 0,
            phase = phase,
            phaseStartEpoch = phaseStart,
            accumulatedExerciseSeconds = exercise,
            accumulatedRestSeconds = rest,
        )

    @Test
    fun `a paused workout gets no chronometer, because one could not be stopped`() {
        val now = 1_700_000_000_000L
        val paused = state(WorkoutPhase.PAUSED, phaseStart = now, exercise = 90L)

        assertNull(paused.chronometerBase(now))
    }

    @Test
    fun `running base stays put while the clock moves, so elapsed grows 1 to 1`() {
        val start = 1_700_000_000_000L
        val running = state(WorkoutPhase.EXERCISE, phaseStart = start, exercise = 30L)
        val expectedBase = start - 30_000L

        assertEquals(expectedBase, running.chronometerBase(start))
        assertEquals(expectedBase, running.chronometerBase(start + 45_000L))
    }

    @Test
    fun `rest counts toward the timer`() {
        val start = 1_700_000_000_000L
        val resting = state(WorkoutPhase.REST, phaseStart = start, exercise = 60L, rest = 10L)
        val now = start + 20_000L

        // 60 s exercise + 10 s rest banked, plus 20 s into the current rest phase = 90 s shown.
        assertEquals(90_000L, now - resting.chronometerBase(now)!!)
    }
}

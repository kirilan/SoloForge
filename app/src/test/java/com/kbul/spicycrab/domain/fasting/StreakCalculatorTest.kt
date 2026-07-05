package com.kbul.spicycrab.domain.fasting

import com.kbul.spicycrab.data.db.entities.FastSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 5)

    @Test
    fun emptyListIsZero() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), zone, today))
    }

    @Test
    fun consecutiveDaysEndingTodayCount() {
        val sessions = listOf(completedOn(today), completedOn(today.minusDays(1)), completedOn(today.minusDays(2)))
        assertEquals(3, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun todayNotYetCompletedFallsBackToYesterday() {
        val sessions = listOf(completedOn(today.minusDays(1)), completedOn(today.minusDays(2)))
        assertEquals(2, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun gapBreaksStreak() {
        val sessions = listOf(completedOn(today), completedOn(today.minusDays(2)))
        assertEquals(1, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun streakEndedBeforeYesterdayIsZero() {
        val sessions = listOf(completedOn(today.minusDays(2)), completedOn(today.minusDays(3)))
        assertEquals(0, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun multipleFastsOnSameDayCountOnce() {
        val sessions = listOf(completedOn(today), completedOn(today), completedOn(today.minusDays(1)))
        assertEquals(2, StreakCalculator.currentStreak(sessions, zone, today))
    }

    @Test
    fun unfinishedSessionsAreIgnored() {
        val active = FastSession(
            modeName = "SIXTEEN_EIGHT",
            targetSeconds = 16 * 3600L,
            eatingWindowSeconds = 8 * 3600L,
            startEpoch = epochAtNoon(today),
            endEpoch = null,
            completed = false,
        )
        assertEquals(0, StreakCalculator.currentStreak(listOf(active), zone, today))
    }

    private fun completedOn(day: LocalDate) = FastSession(
        modeName = "SIXTEEN_EIGHT",
        targetSeconds = 16 * 3600L,
        eatingWindowSeconds = 8 * 3600L,
        startEpoch = epochAtNoon(day) - 16 * 3600_000L,
        endEpoch = epochAtNoon(day),
        completed = true,
    )

    private fun epochAtNoon(day: LocalDate): Long =
        day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}

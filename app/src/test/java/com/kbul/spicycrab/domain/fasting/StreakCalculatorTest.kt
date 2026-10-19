package com.kbul.spicycrab.domain.fasting

import com.kbul.spicycrab.data.db.entities.FastSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class StreakCalculatorTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 5)
    private val now = epochAtNoon(today) + 3600_000L

    @Test
    fun emptyListIsZero() {
        assertEquals(0, StreakCalculator.currentStreak(emptyList(), now, zone, today))
    }

    @Test
    fun consecutiveDaysEndingTodayCount() {
        val sessions = listOf(completedOn(today), completedOn(today.minusDays(1)), completedOn(today.minusDays(2)))
        assertEquals(3, StreakCalculator.currentStreak(sessions, now, zone, today))
    }

    @Test
    fun todayNotYetCompletedFallsBackToYesterday() {
        val sessions = listOf(completedOn(today.minusDays(1)), completedOn(today.minusDays(2)))
        assertEquals(2, StreakCalculator.currentStreak(sessions, now, zone, today))
    }

    @Test
    fun gapBreaksStreak() {
        val sessions = listOf(completedOn(today), completedOn(today.minusDays(2)))
        assertEquals(1, StreakCalculator.currentStreak(sessions, now, zone, today))
    }

    @Test
    fun streakEndedBeforeYesterdayIsZero() {
        val sessions = listOf(completedOn(today.minusDays(2)), completedOn(today.minusDays(3)))
        assertEquals(0, StreakCalculator.currentStreak(sessions, now, zone, today))
    }

    @Test
    fun multipleFastsOnSameDayCountOnce() {
        val sessions = listOf(completedOn(today), completedOn(today), completedOn(today.minusDays(1)))
        assertEquals(2, StreakCalculator.currentStreak(sessions, now, zone, today))
    }

    @Test
    fun activeFastCountsEveryDayItHasReached() {
        val active = session(start = epochAtNoon(today.minusDays(2)), end = null, completed = false)
        assertEquals(3, StreakCalculator.currentStreak(listOf(active), now, zone, today))
    }

    @Test
    fun multiDayFastCountsEveryDayItSpanned() {
        val week = session(start = epochAtNoon(today.minusDays(7)), end = epochAtNoon(today.minusDays(1)), completed = true)
        assertEquals(7, StreakCalculator.currentStreak(listOf(week), now, zone, today))
    }

    @Test
    fun fastEndingAtMidnightDoesNotReachTheNextDay() {
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val fast = session(start = midnight - 16 * 3600_000L, end = midnight, completed = true)
        assertEquals(1, StreakCalculator.currentStreak(listOf(fast), now, zone, today))
    }

    @Test
    fun abandonedFastCountsForNoDays() {
        val abandoned = session(start = epochAtNoon(today) - 3600_000L, end = epochAtNoon(today), completed = false)
        assertEquals(0, StreakCalculator.currentStreak(listOf(abandoned), now, zone, today))
    }

    private fun session(start: Long, end: Long?, completed: Boolean) = FastSession(
        modeName = "CUSTOM",
        targetSeconds = 16 * 3600L,
        eatingWindowSeconds = 0L,
        startEpoch = start,
        endEpoch = end,
        completed = completed,
    )

    private fun completedOn(day: LocalDate) = FastSession(
        modeName = "SIXTEEN_EIGHT",
        targetSeconds = 16 * 3600L,
        eatingWindowSeconds = 8 * 3600L,
        // 02:00–18:00, so the fast stays within [day]; multi-day spans have their own tests.
        startEpoch = epochAtNoon(day) - 10 * 3600_000L,
        endEpoch = epochAtNoon(day) + 6 * 3600_000L,
        completed = true,
    )

    private fun epochAtNoon(day: LocalDate): Long =
        day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
}

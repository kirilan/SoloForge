package com.kbul.spicycrab.ui.home

import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.JournalEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

class CalendarDaysTest {

    private val zone = ZoneId.of("America/New_York")
    private val month = YearMonth.of(2026, 7)
    private val today = LocalDate.of(2026, 7, 5)
    private val now = at(today, 12, 0)

    @Test
    fun gridHas42CellsStartingOnMonday() {
        val days = build()
        assertEquals(42, days.size)
        assertEquals(DayOfWeek.MONDAY, days.first().date.dayOfWeek)
        // July 2026 starts on a Wednesday; the grid leads with Mon Jun 29.
        assertEquals(LocalDate.of(2026, 6, 29), days.first().date)
        assertFalse(days.first().inMonth)
        assertTrue(days.single { it.date == today }.isToday)
    }

    @Test
    fun fastSpanningMidnightAppearsOnBothDays() {
        val fast = fast(start = at(today, 20, 0), end = at(today.plusDays(1), 12, 0))
        val days = build(fasts = listOf(fast))
        assertTrue(days.single { it.date == today }.fasts.contains(fast))
        assertTrue(days.single { it.date == today.plusDays(1) }.fasts.contains(fast))
        assertTrue(days.single { it.date == today.minusDays(1) }.fasts.isEmpty())
    }

    @Test
    fun fastEndingAtMidnightDoesNotAppearOnFollowingDay() {
        val previous = today.minusDays(1)
        val session = fast(
            start = at(previous, 20, 0),
            end = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        )

        val days = build(fasts = listOf(session))

        assertTrue(days.single { it.date == previous }.fasts.contains(session))
        assertFalse(days.single { it.date == today }.fasts.contains(session))
    }

    @Test
    fun activeFastExtendsToNowOnly() {
        val active = fast(start = at(today.minusDays(1), 20, 0), end = null)
        val days = build(fasts = listOf(active))
        assertTrue(days.single { it.date == today.minusDays(1) }.fasts.contains(active))
        assertTrue(days.single { it.date == today }.fasts.contains(active))
        assertTrue(days.single { it.date == today.plusDays(1) }.fasts.isEmpty())
    }

    @Test
    fun calorieBudgetAddsWorkoutBonus() {
        val hourWorkout = WorkoutSession(
            modeName = "SIMPLE",
            startEpoch = at(today, 8, 0),
            endEpoch = at(today, 9, 0),
            totalSeconds = 3600L,
            intervalSeconds = 0,
            exerciseSeconds = 3600L,
            restSeconds = 0L,
            notes = "",
            lastModifiedEpoch = at(today, 9, 0),
        )
        val days = build(workouts = listOf(hourWorkout))
        val day = days.single { it.date == today }
        assertEquals(2000 + 250, day.calorieBudget)
        assertEquals(2000, days.single { it.date == today.minusDays(1) }.calorieBudget)
    }

    @Test
    fun activeWorkoutContributesLiveTimeAndBonus() {
        val started = at(today, 10, 0)
        val active = WorkoutSession(
            modeName = "SIMPLE",
            startEpoch = started,
            endEpoch = null,
            totalSeconds = 0L,
            intervalSeconds = 0,
            exerciseSeconds = 0L,
            restSeconds = 0L,
            notes = "",
            lastModifiedEpoch = started,
            activePhaseName = "EXERCISE",
            phaseStartEpoch = started,
        )

        val day = build(workouts = listOf(active)).single { it.date == today }

        assertEquals(2 * 3600L, day.workoutSeconds)
        assertEquals(2000 + 500, day.calorieBudget)
    }

    @Test
    fun mealAtMidnightBelongsToThatDayOnly() {
        val meal = food(timestamp = at(today, 0, 0))
        val days = build(foods = listOf(meal))
        assertEquals(listOf(meal), days.single { it.date == today }.meals)
        assertTrue(days.single { it.date == today.minusDays(1) }.meals.isEmpty())
    }

    @Test
    fun dstTransitionDayStillBucketsCorrectly() {
        // 2026-03-08 is the US spring-forward day (23 hours long).
        val dstMonth = YearMonth.of(2026, 3)
        val dstDay = LocalDate.of(2026, 3, 8)
        val meal = food(timestamp = at(dstDay, 15, 0))
        val days = buildCalendarDays(
            month = dstMonth,
            selectedToday = dstDay,
            now = at(dstDay, 20, 0),
            zone = zone,
            foods = listOf(meal),
            fasts = emptyList(),
            workouts = emptyList(),
            weights = emptyList(),
            journals = emptyList(),
            baseCalorieGoal = 2000,
        )
        assertEquals(listOf(meal), days.single { it.date == dstDay }.meals)
    }

    @Test
    fun journalMatchesByEpochDay() {
        val note = JournalEntry(dateEpochDay = today.toEpochDay(), text = "hi", lastModifiedEpoch = now)
        val days = build(journals = listOf(note))
        assertEquals(note, days.single { it.date == today }.journal)
        assertEquals(null, days.single { it.date == today.plusDays(1) }.journal)
    }

    private fun build(
        fasts: List<FastSession> = emptyList(),
        foods: List<FoodEntry> = emptyList(),
        workouts: List<WorkoutSession> = emptyList(),
        journals: List<JournalEntry> = emptyList(),
    ) = buildCalendarDays(
        month = month,
        selectedToday = today,
        now = now,
        zone = zone,
        foods = foods,
        fasts = fasts,
        workouts = workouts,
        weights = emptyList(),
        journals = journals,
        baseCalorieGoal = 2000,
    )

    private fun at(day: LocalDate, hour: Int, minute: Int): Long =
        LocalDateTime.of(day, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun fast(start: Long, end: Long?) = FastSession(
        modeName = "SIXTEEN_EIGHT",
        targetSeconds = 57600L,
        eatingWindowSeconds = 28800L,
        startEpoch = start,
        endEpoch = end,
        completed = end != null,
    )

    private fun food(timestamp: Long) = FoodEntry(
        timestampEpoch = timestamp,
        lastModifiedEpoch = timestamp,
        itemName = "meal",
        grams = 100.0,
        kcal = 400.0,
        proteinG = 20.0,
        carbsG = 40.0,
        fatG = 10.0,
        fiberG = 5.0,
        comment = "",
        modelUsed = "manual",
        confidence = "user",
        imagePath = null,
    )
}

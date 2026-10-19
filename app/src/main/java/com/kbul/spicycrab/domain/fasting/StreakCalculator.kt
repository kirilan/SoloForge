package com.kbul.spicycrab.domain.fasting

import com.kbul.spicycrab.data.db.entities.FastSession
import java.time.LocalDate
import java.time.ZoneId

object StreakCalculator {

    /**
     * Consecutive days, ending today or yesterday, that a completed or still-running fast reached
     * into. Every day of a multi-day fast counts; an abandoned (incomplete) fast counts for none.
     */
    fun currentStreak(
        sessions: List<FastSession>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Int {
        val fastedDays = sessions
            .filter { it.completed || it.endEpoch == null }
            .flatMap { it.daysSpanned(now, zone) }
            .toSet()
        if (fastedDays.isEmpty()) return 0

        var streak = 0
        var cursor = today
        if (cursor !in fastedDays) cursor = cursor.minusDays(1)
        while (cursor in fastedDays) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}

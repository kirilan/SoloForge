package com.kbul.spicycrab.domain.fasting

import com.kbul.spicycrab.data.db.entities.FastSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object StreakCalculator {

    fun currentStreak(
        completedSessions: List<FastSession>,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): Int {
        if (completedSessions.isEmpty()) return 0
        val daysWithCompletion = completedSessions
            .mapNotNull { it.endEpoch }
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .toSet()

        var streak = 0
        var cursor = today
        if (cursor !in daysWithCompletion) cursor = cursor.minusDays(1)
        while (cursor in daysWithCompletion) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}

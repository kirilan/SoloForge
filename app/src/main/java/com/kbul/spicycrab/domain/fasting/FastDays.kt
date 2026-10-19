package com.kbul.spicycrab.domain.fasting

import com.kbul.spicycrab.data.db.entities.FastSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Every calendar day this fast reached into; a fast ending exactly at midnight doesn't reach the next day. */
fun FastSession.daysSpanned(now: Long, zone: ZoneId): List<LocalDate> {
    val end = (endEpoch ?: now).coerceAtLeast(startEpoch)
    val first = Instant.ofEpochMilli(startEpoch).atZone(zone).toLocalDate()
    val last = Instant.ofEpochMilli((end - 1).coerceAtLeast(startEpoch)).atZone(zone).toLocalDate()
    return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
}

/** Seconds fasted on each of [days], capped at a full day in case edited sessions overlap. */
fun fastedSecondsByDay(
    sessions: List<FastSession>,
    days: List<LocalDate>,
    now: Long,
    zone: ZoneId,
): List<Long> = days.map { day ->
    val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val ms = sessions.sumOf { s ->
        val overlap = minOf(s.endEpoch ?: now, dayEnd) - maxOf(s.startEpoch, dayStart)
        overlap.coerceAtLeast(0L)
    }
    (ms / 1000L).coerceAtMost((dayEnd - dayStart) / 1000L)
}

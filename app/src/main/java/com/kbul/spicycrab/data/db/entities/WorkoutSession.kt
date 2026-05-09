package com.kbul.spicycrab.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workout_sessions")
data class WorkoutSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeName: String,
    val startEpoch: Long,
    val endEpoch: Long?,
    val totalSeconds: Long,
    val intervalSeconds: Int,
    val exerciseSeconds: Long,
    val restSeconds: Long,
    val notes: String,
    val lastModifiedEpoch: Long,
)

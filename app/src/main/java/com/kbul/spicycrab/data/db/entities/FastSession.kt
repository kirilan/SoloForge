package com.kbul.spicycrab.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "fast_sessions")
data class FastSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val modeName: String,
    val targetSeconds: Long,
    val eatingWindowSeconds: Long,
    val startEpoch: Long,
    val endEpoch: Long?,
    val completed: Boolean,
)

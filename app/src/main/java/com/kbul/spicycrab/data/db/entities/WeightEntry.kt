package com.kbul.spicycrab.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "weight_entries")
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampEpoch: Long,
    val lastModifiedEpoch: Long,
    val weightKg: Double,
    val note: String,
)

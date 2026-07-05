package com.kbul.spicycrab.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey val dateEpochDay: Long,
    val text: String,
    val lastModifiedEpoch: Long,
)

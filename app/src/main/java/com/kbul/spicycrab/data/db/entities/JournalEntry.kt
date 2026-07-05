package com.kbul.spicycrab.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "journal_entries")
data class JournalEntry(
    @PrimaryKey val dateEpochDay: Long,
    val text: String,
    val lastModifiedEpoch: Long,
)

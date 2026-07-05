package com.kbul.spicycrab.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kbul.spicycrab.data.db.entities.JournalEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE dateEpochDay = :dateEpochDay")
    suspend fun deleteByDate(dateEpochDay: Long)

    @Query("SELECT * FROM journal_entries ORDER BY dateEpochDay DESC")
    fun observeAll(): Flow<List<JournalEntry>>
}

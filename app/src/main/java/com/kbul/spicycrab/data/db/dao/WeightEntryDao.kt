package com.kbul.spicycrab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kbul.spicycrab.data.db.entities.WeightEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface WeightEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: WeightEntry): Long

    @Update
    suspend fun update(entry: WeightEntry)

    @Delete
    suspend fun delete(entry: WeightEntry)

    @Query("SELECT * FROM weight_entries ORDER BY timestampEpoch DESC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weight_entries ORDER BY timestampEpoch DESC LIMIT 1")
    suspend fun mostRecent(): WeightEntry?

    @Query("SELECT * FROM weight_entries WHERE healthConnectId = :healthConnectId LIMIT 1")
    suspend fun getByHealthConnectId(healthConnectId: String): WeightEntry?

    @Query("DELETE FROM weight_entries WHERE healthConnectId = :healthConnectId")
    suspend fun deleteByHealthConnectId(healthConnectId: String)

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()
}

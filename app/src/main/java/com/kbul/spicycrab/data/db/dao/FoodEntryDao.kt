package com.kbul.spicycrab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kbul.spicycrab.data.db.entities.FoodEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: FoodEntry): Long

    @Update
    suspend fun update(entry: FoodEntry)

    @Delete
    suspend fun delete(entry: FoodEntry)

    @Query("SELECT * FROM food_entries ORDER BY timestampEpoch DESC")
    fun observeAll(): Flow<List<FoodEntry>>

    @Query("DELETE FROM food_entries")
    suspend fun deleteAll()
}

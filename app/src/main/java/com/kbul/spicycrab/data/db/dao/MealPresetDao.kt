package com.kbul.spicycrab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kbul.spicycrab.data.db.entities.MealPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface MealPresetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: MealPreset): Long

    @Delete
    suspend fun delete(preset: MealPreset)

    @Query("SELECT * FROM meal_presets ORDER BY createdEpoch DESC")
    fun observeAll(): Flow<List<MealPreset>>
}

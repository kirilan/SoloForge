package com.kbul.spicycrab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: WorkoutSession): Long

    @Update
    suspend fun update(session: WorkoutSession)

    @Delete
    suspend fun delete(session: WorkoutSession)

    @Query("SELECT * FROM workout_sessions ORDER BY startEpoch DESC")
    fun observeAll(): Flow<List<WorkoutSession>>

    @Query("SELECT * FROM workout_sessions WHERE endEpoch IS NULL ORDER BY startEpoch DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutSession?>

    @Query("SELECT * FROM workout_sessions WHERE id = :id")
    suspend fun getById(id: Long): WorkoutSession?

    @Query("DELETE FROM workout_sessions")
    suspend fun deleteAll()
}

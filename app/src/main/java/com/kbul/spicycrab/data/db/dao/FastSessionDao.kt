package com.kbul.spicycrab.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.kbul.spicycrab.data.db.entities.FastSession
import kotlinx.coroutines.flow.Flow

@Dao
interface FastSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FastSession): Long

    @Update
    suspend fun update(session: FastSession)

    @Delete
    suspend fun delete(session: FastSession)

    @Query("SELECT * FROM fast_sessions WHERE endEpoch IS NULL ORDER BY startEpoch DESC LIMIT 1")
    fun observeActive(): Flow<FastSession?>

    @Query("SELECT * FROM fast_sessions WHERE endEpoch IS NULL ORDER BY startEpoch DESC LIMIT 1")
    suspend fun getActive(): FastSession?

    @Query("SELECT * FROM fast_sessions ORDER BY startEpoch DESC LIMIT 1")
    suspend fun getMostRecent(): FastSession?

    @Query("SELECT * FROM fast_sessions ORDER BY startEpoch DESC")
    fun observeAll(): Flow<List<FastSession>>

    @Query("SELECT * FROM fast_sessions WHERE completed = 1 ORDER BY endEpoch DESC")
    suspend fun allCompleted(): List<FastSession>

    @Query("DELETE FROM fast_sessions")
    suspend fun deleteAll()
}

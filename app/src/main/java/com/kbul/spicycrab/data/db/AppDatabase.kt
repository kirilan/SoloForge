package com.kbul.spicycrab.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kbul.spicycrab.data.db.dao.FastSessionDao
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.dao.JournalEntryDao
import com.kbul.spicycrab.data.db.dao.MealPresetDao
import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.JournalEntry
import com.kbul.spicycrab.data.db.entities.MealPreset
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession

@Database(
    entities = [FastSession::class, FoodEntry::class, WeightEntry::class, WorkoutSession::class, MealPreset::class, JournalEntry::class],
    version = 9,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun fastSessionDao(): FastSessionDao
    abstract fun foodEntryDao(): FoodEntryDao
    abstract fun weightEntryDao(): WeightEntryDao
    abstract fun workoutSessionDao(): WorkoutSessionDao
    abstract fun mealPresetDao(): MealPresetDao
    abstract fun journalEntryDao(): JournalEntryDao
}

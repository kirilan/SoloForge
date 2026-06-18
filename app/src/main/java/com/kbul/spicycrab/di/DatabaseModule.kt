package com.kbul.spicycrab.di

import android.content.Context
import androidx.room.Room
import com.kbul.spicycrab.data.db.ALL_MIGRATIONS
import com.kbul.spicycrab.data.db.AppDatabase
import com.kbul.spicycrab.data.db.dao.FastSessionDao
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.dao.MealPresetDao
import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "spicycrab.db")
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideFastSessionDao(db: AppDatabase): FastSessionDao = db.fastSessionDao()

    @Provides
    fun provideFoodEntryDao(db: AppDatabase): FoodEntryDao = db.foodEntryDao()

    @Provides
    fun provideWeightEntryDao(db: AppDatabase): WeightEntryDao = db.weightEntryDao()

    @Provides
    fun provideWorkoutSessionDao(db: AppDatabase): WorkoutSessionDao = db.workoutSessionDao()

    @Provides
    fun provideMealPresetDao(db: AppDatabase): MealPresetDao = db.mealPresetDao()
}

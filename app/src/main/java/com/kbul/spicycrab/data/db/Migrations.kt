package com.kbul.spicycrab.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Schema deltas:
 *   v1: fast_sessions
 *   v2: + food_entries, + weight_entries
 *   v3: food_entries gains lastModifiedEpoch
 *   v4: + workout_sessions
 *   v5: weight_entries gains lastModifiedEpoch
 *   v6: + meal_presets
 *   v7: + journal_entries
 *   v8: weight_entries & workout_sessions gain healthConnectId (nullable)
 *
 * Early development used `fallbackToDestructiveMigration()`, so v1–v3 schema
 * JSONs were never exported. The migrations below exist as defensive paths
 * (and documentation of the deltas) but are not unit-tested against legacy
 * schemas because those schemas don't exist.
 *
 * Going forward (v4 → v5 → ...): bump `AppDatabase.version`, add a new
 * Migration object here, build once to generate `app/schemas/<n>.json`, then
 * add a `MigrationTestHelper`-based test under `app/src/androidTest/...`.
 */

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS food_entries (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                comment TEXT NOT NULL,
                modelUsed TEXT NOT NULL,
                confidence TEXT NOT NULL,
                imagePath TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS weight_entries (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                weightKg REAL NOT NULL,
                note TEXT NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Can't ALTER TABLE ADD COLUMN with NOT NULL DEFAULT — Room's schema check
        // would see a mismatched defaultValue. Rename + recreate + copy is the
        // standard Room workaround. Backfills lastModifiedEpoch from timestampEpoch
        // (treats existing rows as never edited).
        db.execSQL(
            """
            CREATE TABLE food_entries_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                itemName TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                comment TEXT NOT NULL,
                modelUsed TEXT NOT NULL,
                confidence TEXT NOT NULL,
                imagePath TEXT
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO food_entries_new
                (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal,
                 proteinG, carbsG, fatG, fiberG, comment, modelUsed, confidence, imagePath)
            SELECT id, timestampEpoch, timestampEpoch, itemName, grams, kcal,
                   proteinG, carbsG, fatG, fiberG, comment, modelUsed, confidence, imagePath
            FROM food_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE food_entries")
        db.execSQL("ALTER TABLE food_entries_new RENAME TO food_entries")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS workout_sessions (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                modeName TEXT NOT NULL,
                startEpoch INTEGER NOT NULL,
                endEpoch INTEGER,
                totalSeconds INTEGER NOT NULL,
                intervalSeconds INTEGER NOT NULL,
                exerciseSeconds INTEGER NOT NULL,
                restSeconds INTEGER NOT NULL,
                notes TEXT NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE weight_entries_new (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                timestampEpoch INTEGER NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                weightKg REAL NOT NULL,
                note TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO weight_entries_new
                (id, timestampEpoch, lastModifiedEpoch, weightKg, note)
            SELECT id, timestampEpoch, timestampEpoch, weightKg, note
            FROM weight_entries
            """.trimIndent()
        )
        db.execSQL("DROP TABLE weight_entries")
        db.execSQL("ALTER TABLE weight_entries_new RENAME TO weight_entries")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS meal_presets (
                id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                grams REAL NOT NULL,
                kcal REAL NOT NULL,
                proteinG REAL NOT NULL,
                carbsG REAL NOT NULL,
                fatG REAL NOT NULL,
                fiberG REAL NOT NULL,
                comment TEXT NOT NULL,
                createdEpoch INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS journal_entries (
                dateEpochDay INTEGER NOT NULL,
                text TEXT NOT NULL,
                lastModifiedEpoch INTEGER NOT NULL,
                PRIMARY KEY(dateEpochDay)
            )
            """.trimIndent()
        )
    }
}

// Nullable, no default → plain ADD COLUMN (no Room defaultValue mismatch, unlike MIGRATION_2_3).
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE weight_entries ADD COLUMN healthConnectId TEXT")
        db.execSQL("ALTER TABLE workout_sessions ADD COLUMN healthConnectId TEXT")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)

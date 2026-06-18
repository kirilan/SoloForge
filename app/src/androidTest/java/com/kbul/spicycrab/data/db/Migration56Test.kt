package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration56Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate5To6AddsMealPresetsAndPreservesFood() {
        val dbName = "migration-5-6-test"
        helper.createDatabase(dbName, 5).apply {
            insertFood(id = 3, name = "Chicken & rice", kcal = 540.0)
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)

        migrated.query("SELECT id, itemName, kcal FROM food_entries").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getLong(0) == 3L)
            check(cursor.getString(1) == "Chicken & rice")
            check(cursor.getDouble(2) == 540.0)
        }

        migrated.execSQL(
            "INSERT INTO meal_presets (id, name, grams, kcal, proteinG, carbsG, fatG, fiberG, comment, createdEpoch) " +
                "VALUES (1, 'Prep meal A', 400.0, 620.0, 50.0, 60.0, 18.0, 8.0, 'batch cooked', 1000)"
        )
        migrated.query("SELECT name, kcal FROM meal_presets WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "Prep meal A")
            check(cursor.getDouble(1) == 620.0)
        }
        migrated.close()
    }
}

private fun SupportSQLiteDatabase.insertFood(id: Long, name: String, kcal: Double) {
    execSQL(
        "INSERT INTO food_entries (id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, " +
            "proteinG, carbsG, fatG, fiberG, comment, modelUsed, confidence, imagePath) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        arrayOf(id, 1_000L, 1_000L, name, 300.0, kcal, 40.0, 50.0, 12.0, 5.0, "", "manual", "user", null),
    )
}

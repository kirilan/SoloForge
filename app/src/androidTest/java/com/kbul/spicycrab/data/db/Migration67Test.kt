package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration67Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate6To7AddsJournalAndPreservesPresets() {
        val dbName = "migration-6-7-test"
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                "INSERT INTO meal_presets (id, name, grams, kcal, proteinG, carbsG, fatG, fiberG, comment, createdEpoch) " +
                    "VALUES (1, 'Prep meal A', 400.0, 620.0, 50.0, 60.0, 18.0, 8.0, 'batch cooked', 1000)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)

        migrated.query("SELECT name, kcal FROM meal_presets WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "Prep meal A")
            check(cursor.getDouble(1) == 620.0)
        }

        migrated.execSQL(
            "INSERT INTO journal_entries (dateEpochDay, text, lastModifiedEpoch) VALUES (20000, 'Felt strong today', 5000)"
        )
        migrated.query("SELECT text, lastModifiedEpoch FROM journal_entries WHERE dateEpochDay = 20000").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "Felt strong today")
            check(cursor.getLong(1) == 5000L)
        }
        migrated.close()
    }
}

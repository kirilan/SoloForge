package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration78Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate7To8AddsHealthConnectIdAndPreservesRows() {
        val dbName = "migration-7-8-test"
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO weight_entries (id, timestampEpoch, lastModifiedEpoch, weightKg, note) " +
                    "VALUES (1, 1000, 1000, 72.5, 'morning')"
            )
            execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, modeName, startEpoch, endEpoch, totalSeconds, intervalSeconds, exerciseSeconds, restSeconds, notes, lastModifiedEpoch) " +
                    "VALUES (1, 'SIMPLE', 2000, 2600, 600, 0, 0, 0, 'run', 2600)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)

        // Existing rows survive with a NULL healthConnectId (locally-created semantics).
        migrated.query("SELECT weightKg, healthConnectId FROM weight_entries WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getDouble(0) == 72.5)
            check(cursor.isNull(1))
        }
        migrated.query("SELECT notes, healthConnectId FROM workout_sessions WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "run")
            check(cursor.isNull(1))
        }

        // New column is writable (imported-row path).
        migrated.execSQL("UPDATE weight_entries SET healthConnectId = 'hc-abc' WHERE id = 1")
        migrated.query("SELECT healthConnectId FROM weight_entries WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "hc-abc")
        }
        migrated.close()
    }
}

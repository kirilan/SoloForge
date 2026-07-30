package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration89Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate8To9AddsDurableWorkoutStateAndPreservesRows() {
        val dbName = "migration-8-9-test"
        helper.createDatabase(dbName, 8).apply {
            execSQL(
                "INSERT INTO workout_sessions " +
                    "(id, modeName, startEpoch, endEpoch, totalSeconds, intervalSeconds, exerciseSeconds, restSeconds, notes, lastModifiedEpoch, healthConnectId) " +
                    "VALUES (1, 'EXERCISE_REST', 2000, NULL, 0, 0, 120, 30, 'active', 2150, NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9)

        migrated.query(
            "SELECT exerciseSeconds, restSeconds, activePhaseName, phaseStartEpoch " +
                "FROM workout_sessions WHERE id = 1"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getLong(0) == 120L)
            check(cursor.getLong(1) == 30L)
            check(cursor.isNull(2))
            check(cursor.isNull(3))
        }
        migrated.execSQL(
            "UPDATE workout_sessions SET activePhaseName = 'REST', phaseStartEpoch = 3000 WHERE id = 1"
        )
        migrated.query(
            "SELECT activePhaseName, phaseStartEpoch FROM workout_sessions WHERE id = 1"
        ).use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "REST")
            check(cursor.getLong(1) == 3000L)
        }
        migrated.close()
    }
}

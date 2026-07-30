package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration45Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate4To5BackfillsWeightLastModified() {
        val dbName = "migration-4-5-test"
        helper.createDatabase(dbName, 4).apply {
            insertWeight(id = 7, timestamp = 123_456L, weightKg = 82.5, note = "baseline")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)
        migrated.query("SELECT id, timestampEpoch, lastModifiedEpoch, weightKg, note FROM weight_entries").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getLong(0) == 7L)
            check(cursor.getLong(1) == 123_456L)
            check(cursor.getLong(2) == 123_456L)
            check(cursor.getDouble(3) == 82.5)
            check(cursor.getString(4) == "baseline")
        }
        migrated.close()
    }
}

private fun SupportSQLiteDatabase.insertWeight(id: Long, timestamp: Long, weightKg: Double, note: String) {
    execSQL(
        "INSERT INTO weight_entries (id, timestampEpoch, weightKg, note) VALUES (?, ?, ?, ?)",
        arrayOf<Any>(id, timestamp, weightKg, note),
    )
}

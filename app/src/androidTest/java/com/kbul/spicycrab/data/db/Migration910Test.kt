package com.kbul.spicycrab.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration910Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrate9To10AddsFoodHealthConnectIdAndPreservesRows() {
        val dbName = "migration-9-10-test"
        helper.createDatabase(dbName, 9).apply {
            execSQL(
                "INSERT INTO food_entries " +
                    "(id, timestampEpoch, lastModifiedEpoch, itemName, grams, kcal, proteinG, carbsG, fatG, fiberG, comment, modelUsed, confidence, imagePath) " +
                    "VALUES (1, 1000, 1000, 'oats', 80, 300, 10, 54, 6, 8, '', 'manual', 'user', NULL)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 10, true, MIGRATION_9_10)

        migrated.query("SELECT kcal, healthConnectId FROM food_entries WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getDouble(0) == 300.0)
            check(cursor.isNull(1))
        }
        migrated.execSQL("UPDATE food_entries SET healthConnectId = 'hc-food' WHERE id = 1")
        migrated.query("SELECT healthConnectId FROM food_entries WHERE id = 1").use { cursor ->
            check(cursor.moveToFirst())
            check(cursor.getString(0) == "hc-food")
        }
        migrated.close()
    }
}

package com.kbul.spicycrab.data.backup

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the promise that any backup ever written can be imported by every
 * future version. backup-v1.json is frozen — never edit it. If decoding it
 * breaks, the format change is a compatibility break for users' backups.
 */
class BackupFormatCompatTest {

    private fun fixture(): String =
        javaClass.getResourceAsStream("/backup-v1.json")!!.readBytes().decodeToString()

    @Test
    fun v1FixtureDecodesWithAllFieldsIntact() {
        val b = BackupJson.decodeFromString<BackupFile>(fixture())

        assertEquals(1, b.schemaVersion)

        assertEquals(1800, b.settings.goals.kcal)
        assertEquals("EIGHTEEN_SIX", b.settings.defaultFastingModeName)
        assertEquals(false, b.settings.weightUnitKg)
        assertEquals(true, b.settings.weighInEnabled)
        assertNull(b.settings.exportFolderUri)

        assertEquals(2, b.fasts.size)
        val active = b.fasts.single { it.endEpoch == null }
        assertEquals("EIGHTEEN_SIX", active.modeName)
        assertEquals(1783250000000L, active.startEpoch)

        val food = b.foods.single()
        assertEquals("Chicken & rice", food.itemName)
        assertEquals(540.5, food.kcal, 0.0)
        assertEquals("post-workout, extra sauce", food.comment)

        val weight = b.weights.single()
        assertEquals(82.4, weight.weightKg, 0.0)
        assertEquals("morning, after fast", weight.note)

        val workout = b.workouts.single()
        assertEquals(2700L, workout.totalSeconds)
        assertEquals("EXERCISE_REST", workout.modeName)

        val preset = b.presets.single()
        assertEquals("Overnight oats", preset.name)
        assertEquals(9.5, preset.fiberG, 0.0)

        val note = b.journal.single()
        assertEquals(20639L, note.dateEpochDay)
        assertTrue(note.text.contains("Evening walk"))
    }

    @Test
    fun unknownFieldsFromNewerMinorVersionsAreIgnored() {
        val withExtras = fixture().replaceFirst(
            "\"schemaVersion\": 1,",
            "\"schemaVersion\": 1, \"someFutureField\": {\"nested\": true},",
        )
        val b = BackupJson.decodeFromString<BackupFile>(withExtras)
        assertEquals(1, b.schemaVersion)
        assertEquals(2, b.fasts.size)
    }

    @Test
    fun encodeDecodeRoundTripIsLossless() {
        val original = BackupJson.decodeFromString<BackupFile>(fixture())
        val roundTripped = BackupJson.decodeFromString<BackupFile>(BackupJson.encodeToString(original))
        assertEquals(original, roundTripped)
    }
}

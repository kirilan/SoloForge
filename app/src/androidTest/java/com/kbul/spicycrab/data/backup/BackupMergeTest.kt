package com.kbul.spicycrab.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kbul.spicycrab.data.db.AppDatabase
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.JournalEntry
import com.kbul.spicycrab.data.db.entities.MealPreset
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.prefs.AppSettings
import com.kbul.spicycrab.data.prefs.NutritionGoals
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.workout.WorkoutStateHolder
import com.kbul.spicycrab.domain.health.HealthConnectRepository
import com.kbul.spicycrab.notifications.ReminderScheduler
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupMergeTest {

    private lateinit var db: AppDatabase
    private lateinit var manager: BackupManager

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val settings = SettingsRepo(context)
        manager = BackupManager(
            context = context,
            db = db,
            fastDao = db.fastSessionDao(),
            foodDao = db.foodEntryDao(),
            weightDao = db.weightEntryDao(),
            workoutDao = db.workoutSessionDao(),
            presetDao = db.mealPresetDao(),
            journalDao = db.journalEntryDao(),
            settingsRepo = settings,
            reminderScheduler = ReminderScheduler(context),
            workoutStateHolder = WorkoutStateHolder(),
            healthConnect = HealthConnectRepository(
                context = context,
                settings = settings,
                weightDao = db.weightEntryDao(),
                workoutDao = db.workoutSessionDao(),
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun mergeDedupesConcatenatesJournalAndIsIdempotent() = runBlocking {
        val fastDao = db.fastSessionDao()
        val weightDao = db.weightEntryDao()
        val presetDao = db.mealPresetDao()
        val journalDao = db.journalEntryDao()

        fastDao.insert(fast(startEpoch = 1_000, endEpoch = 2_000))
        fastDao.insert(fast(startEpoch = 9_000, endEpoch = null))
        weightDao.insert(WeightEntry(timestampEpoch = 100, lastModifiedEpoch = 100, weightKg = 80.0, note = ""))
        presetDao.insert(preset("Oats"))
        journalDao.upsert(JournalEntry(dateEpochDay = 20_000, text = "local note", lastModifiedEpoch = 50))

        val backup = BackupFile(
            exportedAtEpoch = 0,
            settings = defaultSettings(),
            fasts = listOf(
                fast(startEpoch = 1_000, endEpoch = 2_000),   // duplicate -> skipped
                fast(startEpoch = 3_000, endEpoch = 4_000),   // new -> added
                fast(startEpoch = 8_000, endEpoch = null),    // second active -> skipped, local wins
            ),
            foods = emptyList(),
            weights = listOf(
                WeightEntry(timestampEpoch = 100, lastModifiedEpoch = 999, weightKg = 79.0, note = "edited"), // same key -> local wins
                WeightEntry(timestampEpoch = 200, lastModifiedEpoch = 200, weightKg = 81.0, note = ""),        // new -> added
            ),
            workouts = emptyList(),
            presets = listOf(preset("Oats"), preset("Shake")),
            journal = listOf(
                JournalEntry(dateEpochDay = 20_000, text = "backup note", lastModifiedEpoch = 60), // differs -> concat
                JournalEntry(dateEpochDay = 20_001, text = "fresh day", lastModifiedEpoch = 70),   // new -> added
            ),
        )

        val added = manager.mergeInto(backup)
        check(added == 5) { "expected 5 added, got $added" }

        val fasts = fastDao.observeAll().first()
        check(fasts.size == 3)
        check(fasts.count { it.endEpoch == null } == 1) { "exactly one active fast must survive" }
        check(fasts.single { it.endEpoch == null }.startEpoch == 9_000L) { "local active fast wins" }

        val weights = weightDao.observeAll().first()
        check(weights.size == 2)
        check(weights.single { it.timestampEpoch == 100L }.weightKg == 80.0) { "local wins on conflict" }

        check(presetDao.observeAll().first().map { it.name }.sorted() == listOf("Oats", "Shake"))

        val mergedNote = journalDao.observeAll().first().single { it.dateEpochDay == 20_000L }
        check(mergedNote.text == "local note\n---\nbackup note") { "journal concatenates: ${mergedNote.text}" }

        // Idempotence: importing the same file again must change nothing.
        val addedAgain = manager.mergeInto(backup)
        check(addedAgain == 0) { "second merge must be a no-op, added $addedAgain" }
        check(fastDao.observeAll().first().size == 3)
        check(journalDao.observeAll().first().single { it.dateEpochDay == 20_000L }.text == mergedNote.text)
    }

    @Test
    fun replaceWipesLocalRows() = runBlocking {
        db.weightEntryDao().insert(WeightEntry(timestampEpoch = 1, lastModifiedEpoch = 1, weightKg = 70.0, note = "old"))

        val backup = BackupFile(
            exportedAtEpoch = 0,
            settings = defaultSettings(),
            fasts = emptyList(),
            foods = emptyList(),
            weights = listOf(WeightEntry(id = 42, timestampEpoch = 2, lastModifiedEpoch = 2, weightKg = 90.0, note = "new")),
            workouts = emptyList(),
            presets = emptyList(),
            journal = emptyList(),
        )

        manager.replaceWith(backup)

        val weights = db.weightEntryDao().observeAll().first()
        check(weights.size == 1)
        check(weights.single().weightKg == 90.0)
        check(weights.single().id == 42L)
    }

    @Test
    fun malformedBackupCannotCreateMultipleActiveFasts() = runBlocking {
        val backup = BackupFile(
            exportedAtEpoch = 0,
            settings = defaultSettings(),
            fasts = listOf(
                fast(startEpoch = 1_000, endEpoch = null),
                fast(startEpoch = 2_000, endEpoch = null),
            ),
            foods = emptyList(),
            weights = emptyList(),
            workouts = emptyList(),
            presets = emptyList(),
            journal = emptyList(),
        )

        manager.replaceWith(backup)

        val fasts = db.fastSessionDao().observeAll().first()
        check(fasts.count { it.endEpoch == null } == 1)
        check(fasts.single().startEpoch == 2_000L)
    }

    private fun fast(startEpoch: Long, endEpoch: Long?) = FastSession(
        modeName = "SIXTEEN_EIGHT",
        targetSeconds = 16 * 3600L,
        eatingWindowSeconds = 8 * 3600L,
        startEpoch = startEpoch,
        endEpoch = endEpoch,
        completed = endEpoch != null,
    )

    private fun preset(name: String) = MealPreset(
        name = name,
        grams = 100.0,
        kcal = 350.0,
        proteinG = 12.0,
        carbsG = 60.0,
        fatG = 6.0,
        fiberG = 9.0,
        comment = "",
        createdEpoch = 1_000,
    )

    private fun defaultSettings() = AppSettings(
        exportFolderUri = null,
        savePhotoLocally = false,
        aiFeaturesEnabled = true,
        weightUnitKg = true,
        goals = NutritionGoals(2000, 150, 220, 65, 30),
        weighInEnabled = false,
        weighInDayOfWeek = 1,
        weighInHour = 8,
        weighInMinute = 0,
        defaultFastingModeName = "SIXTEEN_EIGHT",
        almostThereEnabled = true,
        eatingWindowClosingEnabled = true,
        showFastingTab = true,
        showFoodTab = true,
        showWeightTab = true,
        showWorkoutTab = true,
        onboardingComplete = true,
        healthImportEnabled = false,
        healthExportEnabled = false,
        healthLastSyncEpoch = 0L,
    )
}

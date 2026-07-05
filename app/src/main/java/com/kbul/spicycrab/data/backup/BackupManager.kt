package com.kbul.spicycrab.data.backup

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.kbul.spicycrab.data.db.AppDatabase
import com.kbul.spicycrab.data.db.dao.FastSessionDao
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.dao.JournalEntryDao
import com.kbul.spicycrab.data.db.dao.MealPresetDao
import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.JournalEntry
import com.kbul.spicycrab.data.db.entities.MealPreset
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.data.prefs.AppSettings
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.fasting.FastingMode
import com.kbul.spicycrab.notifications.FastingNotificationService
import com.kbul.spicycrab.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

internal val BackupJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

@Serializable
data class BackupFile(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAtEpoch: Long,
    val settings: AppSettings,
    val fasts: List<FastSession>,
    val foods: List<FoodEntry>,
    val weights: List<WeightEntry>,
    val workouts: List<WorkoutSession>,
    val presets: List<MealPreset>,
    val journal: List<JournalEntry>,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class ImportSummary(val added: Int, val replaced: Boolean)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: AppDatabase,
    private val fastDao: FastSessionDao,
    private val foodDao: FoodEntryDao,
    private val weightDao: WeightEntryDao,
    private val workoutDao: WorkoutSessionDao,
    private val presetDao: MealPresetDao,
    private val journalDao: JournalEntryDao,
    private val settingsRepo: SettingsRepo,
    private val reminderScheduler: ReminderScheduler,
) {
    private val json = BackupJson
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(FlowPreview::class)
    fun startAutoBackup() {
        scope.launch {
            combine(
                listOf<Flow<Any?>>(
                    fastDao.observeAll(),
                    foodDao.observeAll(),
                    weightDao.observeAll(),
                    workoutDao.observeAll(),
                    presetDao.observeAll(),
                    journalDao.observeAll(),
                    settingsRepo.settings,
                )
            ) { }
                .debounce(AUTO_BACKUP_DEBOUNCE_MS)
                .collect {
                    settingsRepo.current().exportFolderUri?.let { uriStr ->
                        runCatching { writeToFolder(Uri.parse(uriStr)) }
                    }
                }
        }
    }

    suspend fun exportTo(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")
                ?.use { it.write(json.encodeToString(buildBackup()).toByteArray()) }
                ?: error("Cannot open output stream")
        }
    }

    suspend fun importFrom(uri: Uri, merge: Boolean): Result<ImportSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().decodeToString() }
                ?: error("Cannot read file")
            val backup = json.decodeFromString<BackupFile>(text)
            check(backup.schemaVersion <= BackupFile.SCHEMA_VERSION) {
                "This backup comes from a newer version of Solo Forge. Update the app first."
            }
            val added = db.withTransaction {
                if (merge) mergeInto(backup) else replaceWith(backup)
            }
            if (!merge) settingsRepo.applyBackup(backup.settings)
            syncFastingSideEffects()
            ImportSummary(added, replaced = !merge)
        }
    }

    private suspend fun buildBackup() = BackupFile(
        exportedAtEpoch = System.currentTimeMillis(),
        settings = settingsRepo.current(),
        fasts = fastDao.observeAll().first(),
        foods = foodDao.observeAll().first(),
        weights = weightDao.observeAll().first(),
        workouts = workoutDao.observeAll().first(),
        presets = presetDao.observeAll().first(),
        journal = journalDao.observeAll().first(),
    )

    private suspend fun writeToFolder(folderUri: Uri) {
        val payload = json.encodeToString(buildBackup())
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: error("Cannot open folder")
        val file = tree.findFile(AUTO_BACKUP_NAME)
            ?: tree.createFile("application/json", AUTO_BACKUP_NAME)
            ?: error("Cannot create backup file")
        context.contentResolver.openOutputStream(file.uri, "wt")
            ?.use { it.write(payload.toByteArray()) }
            ?: error("Cannot open output stream")
    }

    internal suspend fun replaceWith(b: BackupFile): Int {
        fastDao.deleteAll()
        foodDao.deleteAll()
        weightDao.deleteAll()
        workoutDao.deleteAll()
        presetDao.deleteAll()
        journalDao.deleteAll()
        b.fasts.forEach { fastDao.insert(it.copy(id = 0)) }
        b.foods.forEach { foodDao.insert(it.copy(id = 0)) }
        b.weights.forEach { weightDao.insert(it.copy(id = 0)) }
        b.workouts.filter { it.endEpoch != null }.forEach { workoutDao.insert(it.copy(id = 0)) }
        b.presets.forEach { presetDao.insert(it.copy(id = 0)) }
        b.journal.forEach { journalDao.upsert(it) }
        return b.fasts.size + b.foods.size + b.weights.size + b.workouts.size + b.presets.size + b.journal.size
    }

    // Merge policy: union deduped on natural keys (timestamps, preset name, journal date);
    // on conflict local wins, except journal text which concatenates. Importing the same
    // file twice must change nothing.
    internal suspend fun mergeInto(b: BackupFile): Int {
        var added = 0

        val localFasts = fastDao.observeAll().first()
        val fastStarts = localFasts.map { it.startEpoch }.toSet()
        val hasLocalActive = localFasts.any { it.endEpoch == null }
        b.fasts
            .filter { it.startEpoch !in fastStarts && (it.endEpoch != null || !hasLocalActive) }
            .forEach { fastDao.insert(it.copy(id = 0)); added++ }

        val foodTimes = foodDao.observeAll().first().map { it.timestampEpoch }.toSet()
        b.foods.filter { it.timestampEpoch !in foodTimes }
            .forEach { foodDao.insert(it.copy(id = 0)); added++ }

        val weightTimes = weightDao.observeAll().first().map { it.timestampEpoch }.toSet()
        b.weights.filter { it.timestampEpoch !in weightTimes }
            .forEach { weightDao.insert(it.copy(id = 0)); added++ }

        val workoutStarts = workoutDao.observeAll().first().map { it.startEpoch }.toSet()
        b.workouts.filter { it.endEpoch != null && it.startEpoch !in workoutStarts }
            .forEach { workoutDao.insert(it.copy(id = 0)); added++ }

        val presetNames = presetDao.observeAll().first().map { it.name }.toSet()
        b.presets.filter { it.name !in presetNames }
            .forEach { presetDao.insert(it.copy(id = 0)); added++ }

        val localJournal = journalDao.observeAll().first().associateBy { it.dateEpochDay }
        b.journal.forEach { imported ->
            val local = localJournal[imported.dateEpochDay]
            when {
                local == null -> {
                    journalDao.upsert(imported)
                    added++
                }
                local.text.contains(imported.text) -> Unit
                else -> {
                    journalDao.upsert(
                        local.copy(
                            text = local.text + "\n---\n" + imported.text,
                            lastModifiedEpoch = maxOf(local.lastModifiedEpoch, imported.lastModifiedEpoch),
                        )
                    )
                    added++
                }
            }
        }
        return added
    }

    private suspend fun syncFastingSideEffects() {
        val active = fastDao.getActive()
        if (active == null) {
            context.startService(FastingNotificationService.stopIntent(context))
            reminderScheduler.cancelAlmostThere()
        } else {
            val mode = FastingMode.fromName(active.modeName)
            ContextCompat.startForegroundService(
                context,
                FastingNotificationService.startIntent(context, active.startEpoch, active.targetSeconds, mode.displayName),
            )
            if (settingsRepo.current().almostThereEnabled) {
                reminderScheduler.scheduleAlmostThere(active.startEpoch, mode)
            }
        }
    }

    private companion object {
        const val AUTO_BACKUP_NAME = "SoloForge-backup.json"
        const val AUTO_BACKUP_DEBOUNCE_MS = 3_000L
    }
}

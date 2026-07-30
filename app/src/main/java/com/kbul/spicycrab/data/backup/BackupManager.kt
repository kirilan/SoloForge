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
import com.kbul.spicycrab.domain.health.HealthConnectRepository
import com.kbul.spicycrab.domain.nutrition.FoodPhotoFiles
import com.kbul.spicycrab.domain.workout.WorkoutStateHolder
import com.kbul.spicycrab.notifications.FastingNotificationService
import com.kbul.spicycrab.notifications.ReminderScheduler
import com.kbul.spicycrab.notifications.WorkoutNotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalTime
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

data class ImportSummary(
    val added: Int,
    val replaced: Boolean,
    val warning: Boolean = false,
)
data class AutoBackupStatus(
    val lastSuccessEpoch: Long = 0L,
    val failed: Boolean = false,
)

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
    private val workoutStateHolder: WorkoutStateHolder,
    private val healthConnect: HealthConnectRepository,
) {
    private val json = BackupJson
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _autoBackupStatus = MutableStateFlow(AutoBackupStatus())
    val autoBackupStatus: StateFlow<AutoBackupStatus> = _autoBackupStatus.asStateFlow()

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
                    val uriStr = settingsRepo.current().exportFolderUri
                    if (uriStr == null) {
                        _autoBackupStatus.value = AutoBackupStatus()
                    } else {
                        runCatching { writeToFolder(Uri.parse(uriStr)) }
                            .onSuccess {
                                _autoBackupStatus.value = AutoBackupStatus(
                                    lastSuccessEpoch = System.currentTimeMillis(),
                                )
                            }
                            .onFailure {
                                _autoBackupStatus.value = _autoBackupStatus.value.copy(failed = true)
                            }
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
            val replacedFoodPhotos = if (merge) {
                emptyList()
            } else {
                foodDao.observeAll().first().mapNotNull { it.imagePath }
            }
            val added = db.withTransaction {
                if (merge) mergeInto(backup) else replaceWith(backup)
            }
            var warning = false
            if (!merge) {
                replacedFoodPhotos.forEach { FoodPhotoFiles.deleteOwned(context, it) }
                warning = runCatching { discardActiveWorkout() }.isFailure || warning
                warning = runCatching { settingsRepo.applyBackup(backup.settings) }.isFailure || warning
                warning = runCatching { healthConnect.reconcileEnabledSettings() }.isFailure || warning
                warning = runCatching { syncWeighInReminder() }.isFailure || warning
            }
            warning = runCatching { syncFastingSideEffects() }.isFailure || warning
            ImportSummary(added, replaced = !merge, warning = warning)
        }
    }

    private suspend fun buildBackup() = BackupFile(
        exportedAtEpoch = System.currentTimeMillis(),
        settings = settingsRepo.current(),
        fasts = fastDao.observeAll().first(),
        foods = foodDao.observeAll().first().map { it.copy(imagePath = null) },
        weights = weightDao.observeAll().first(),
        workouts = workoutDao.observeAll().first(),
        presets = presetDao.observeAll().first(),
        journal = journalDao.observeAll().first(),
    )

    private suspend fun writeToFolder(folderUri: Uri) {
        val payload = json.encodeToString(buildBackup())
        val tree = DocumentFile.fromTreeUri(context, folderUri) ?: error("Cannot open folder")

        var current = tree.findFile(AUTO_BACKUP_NAME)
        if (current == null) {
            val recoverable = tree.findFile(AUTO_BACKUP_PENDING_NAME)
                ?: tree.findFile(AUTO_BACKUP_PREVIOUS_NAME)
            if (recoverable != null && recoverable.renameTo(AUTO_BACKUP_NAME)) {
                current = tree.findFile(AUTO_BACKUP_NAME)
            }
        }

        tree.findFile(AUTO_BACKUP_PENDING_NAME)?.let {
            check(it.delete()) { "Cannot clear incomplete backup" }
        }
        val pending = tree.createFile("application/json", AUTO_BACKUP_PENDING_NAME)
            ?: error("Cannot create staged backup file")
        context.contentResolver.openOutputStream(pending.uri, "wt")
            ?.use { it.write(payload.toByteArray()) }
            ?: error("Cannot open output stream")

        tree.findFile(AUTO_BACKUP_PREVIOUS_NAME)?.let {
            if (!it.delete()) {
                pending.delete()
                error("Cannot rotate previous backup")
            }
        }
        if (current != null && !current.renameTo(AUTO_BACKUP_PREVIOUS_NAME)) {
            pending.delete()
            error("Backup provider does not support safe replacement")
        }
        if (!pending.renameTo(AUTO_BACKUP_NAME)) {
            tree.findFile(AUTO_BACKUP_PREVIOUS_NAME)?.renameTo(AUTO_BACKUP_NAME)
            error("Cannot promote staged backup")
        }
        tree.findFile(AUTO_BACKUP_PREVIOUS_NAME)?.delete()
    }

    internal suspend fun replaceWith(b: BackupFile): Int {
        fastDao.deleteAll()
        foodDao.deleteAll()
        weightDao.deleteAll()
        workoutDao.deleteAll()
        presetDao.deleteAll()
        journalDao.deleteAll()
        val fasts = b.fasts.filter { it.endEpoch != null } +
            listOfNotNull(b.fasts.filter { it.endEpoch == null }.maxByOrNull { it.startEpoch })
        fasts.forEach { fastDao.insert(it) }
        b.foods.forEach { foodDao.insert(it.copy(imagePath = null)) }
        b.weights.forEach { weightDao.insert(it) }
        val completedWorkouts = b.workouts.filter { it.endEpoch != null }
        completedWorkouts.forEach { workoutDao.insert(it) }
        b.presets.forEach { presetDao.insert(it) }
        b.journal.forEach { journalDao.upsert(it) }
        return fasts.size + b.foods.size + b.weights.size + completedWorkouts.size + b.presets.size + b.journal.size
    }

    // Merge policy: union deduped on natural keys (timestamps, preset name, journal date);
    // on conflict local wins, except journal text which concatenates. Importing the same
    // file twice must change nothing.
    internal suspend fun mergeInto(b: BackupFile): Int {
        var added = 0

        val localFasts = fastDao.observeAll().first()
        val fastStarts = localFasts.mapTo(mutableSetOf()) { it.startEpoch }
        var hasActive = localFasts.any { it.endEpoch == null }
        b.fasts.forEach { imported ->
            if (imported.startEpoch in fastStarts || imported.endEpoch == null && hasActive) return@forEach
            fastDao.insert(imported.copy(id = 0))
            fastStarts += imported.startEpoch
            if (imported.endEpoch == null) hasActive = true
            added++
        }

        val foodTimes = foodDao.observeAll().first().mapTo(mutableSetOf()) { it.timestampEpoch }
        b.foods.forEach { imported ->
            if (!foodTimes.add(imported.timestampEpoch)) return@forEach
            foodDao.insert(imported.copy(id = 0, imagePath = null))
            added++
        }

        val weightTimes = weightDao.observeAll().first().mapTo(mutableSetOf()) { it.timestampEpoch }
        b.weights.forEach { imported ->
            if (!weightTimes.add(imported.timestampEpoch)) return@forEach
            weightDao.insert(imported.copy(id = 0))
            added++
        }

        val workoutStarts = workoutDao.observeAll().first().mapTo(mutableSetOf()) { it.startEpoch }
        b.workouts.forEach { imported ->
            if (imported.endEpoch == null || !workoutStarts.add(imported.startEpoch)) return@forEach
            workoutDao.insert(imported.copy(id = 0))
            added++
        }

        val presetNames = presetDao.observeAll().first().mapTo(mutableSetOf()) { it.name }
        b.presets.forEach { imported ->
            if (!presetNames.add(imported.name)) return@forEach
            presetDao.insert(imported.copy(id = 0))
            added++
        }

        val localJournal = journalDao.observeAll().first()
            .associateByTo(mutableMapOf()) { it.dateEpochDay }
        b.journal.forEach { imported ->
            val local = localJournal[imported.dateEpochDay]
            when {
                local == null -> {
                    journalDao.upsert(imported)
                    localJournal[imported.dateEpochDay] = imported
                    added++
                }
                local.text.contains(imported.text) -> Unit
                else -> {
                    val merged = local.copy(
                        text = local.text + "\n---\n" + imported.text,
                        lastModifiedEpoch = maxOf(local.lastModifiedEpoch, imported.lastModifiedEpoch),
                    )
                    journalDao.upsert(merged)
                    localJournal[imported.dateEpochDay] = merged
                    added++
                }
            }
        }
        return added
    }

    private suspend fun syncFastingSideEffects() {
        reminderScheduler.cancelAlmostThere()
        reminderScheduler.cancelEatingWindowClosing()
        val active = fastDao.getActive()
        if (active == null) {
            context.startService(FastingNotificationService.stopIntent(context))
            val settings = settingsRepo.current()
            if (settings.eatingWindowClosingEnabled) {
                val mostRecent = fastDao.getMostRecentlyCompleted()
                val end = mostRecent?.endEpoch
                if (mostRecent != null && end != null) {
                    reminderScheduler.scheduleEatingWindowClosing(
                        end,
                        FastingMode.fromName(mostRecent.modeName),
                    )
                }
            }
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

    private suspend fun syncWeighInReminder() {
        val settings = settingsRepo.current()
        if (settings.weighInEnabled) {
            reminderScheduler.scheduleWeeklyWeighIn(
                DayOfWeek.of(settings.weighInDayOfWeek),
                LocalTime.of(settings.weighInHour, settings.weighInMinute),
            )
        } else {
            reminderScheduler.cancelWeeklyWeighIn()
        }
    }

    private fun discardActiveWorkout() {
        workoutStateHolder.set(null)
        context.startService(WorkoutNotificationService.discardIntent(context))
    }

    private companion object {
        const val AUTO_BACKUP_NAME = "SoloForge-backup.json"
        const val AUTO_BACKUP_PENDING_NAME = "SoloForge-backup.pending.json"
        const val AUTO_BACKUP_PREVIOUS_NAME = "SoloForge-backup.previous.json"
        const val AUTO_BACKUP_DEBOUNCE_MS = 3_000L
    }
}

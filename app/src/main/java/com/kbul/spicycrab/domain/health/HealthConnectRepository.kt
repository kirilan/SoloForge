package com.kbul.spicycrab.domain.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.DeletionChange
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Mass
import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.data.prefs.AppSettings
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.workout.WorkoutMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The only file that imports Health Connect types. Availability, permissions, and sync. */
@Singleton
class HealthConnectRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settings: SettingsRepo,
    private val weightDao: WeightEntryDao,
    private val workoutDao: WorkoutSessionDao,
) {
    val importPermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
    )
    val exportPermissions = setOf(
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
    )

    /** Contract for `rememberLauncherForActivityResult`; input/returns a Set of HC permission strings. */
    fun permissionRequestContract() = PermissionController.createRequestPermissionResultContract()

    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)
    fun isAvailable(): Boolean = sdkStatus() == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(): Set<String> =
        if (isAvailable()) client().permissionController.getGrantedPermissions() else emptySet()

    private val syncMutex = Mutex()

    /** Runs whatever the toggles + granted permissions allow. Never throws — HC failure must not break the app. */
    suspend fun sync() {
        if (!isAvailable()) return
        val s = settings.settings.first()
        if (!s.healthImportEnabled && !s.healthExportEnabled) return
        syncMutex.withLock {
            val granted = runCatching { grantedPermissions() }.getOrElse { return }
            val (importEnabled, exportEnabled) = reconcileEnabledSettings(s, granted)
            if (!importEnabled && !exportEnabled) return@withLock
            val importSucceeded = !importEnabled ||
                granted.any { it in importPermissions } && runCatching { import(granted) }.isSuccess
            val exportSucceeded = !exportEnabled ||
                granted.any { it in exportPermissions } && runCatching { export(granted) }.isSuccess
            if (importSucceeded && exportSucceeded) {
                settings.setHealthLastSync(System.currentTimeMillis())
            }
        }
    }

    suspend fun reconcileEnabledSettings() {
        if (!isAvailable()) return
        val granted = runCatching { grantedPermissions() }.getOrElse { return }
        reconcileEnabledSettings(settings.settings.first(), granted)
    }

    private suspend fun reconcileEnabledSettings(
        current: AppSettings,
        granted: Set<String>,
    ): Pair<Boolean, Boolean> {
        var importEnabled = current.healthImportEnabled
        var exportEnabled = current.healthExportEnabled
        if (importEnabled && granted.none { it in importPermissions }) {
            settings.setHealthImportEnabled(false)
            importEnabled = false
        }
        if (exportEnabled && granted.none { it in exportPermissions }) {
            settings.setHealthExportEnabled(false)
            exportEnabled = false
        }
        return importEnabled to exportEnabled
    }

    /** Removes our exported copy when a locally-created row is deleted. Imported rows are one-way: never propagated. */
    suspend fun onLocalWeightDeleted(entry: WeightEntry) {
        if (entry.healthConnectId == null) deleteExported(WeightRecord::class, "sf-weight-${entry.id}")
    }

    suspend fun onLocalWorkoutDeleted(session: WorkoutSession) {
        if (session.healthConnectId == null) deleteExported(ExerciseSessionRecord::class, "sf-workout-${session.id}")
    }

    private suspend fun deleteExported(type: KClass<out Record>, clientRecordId: String) {
        if (!isAvailable() || !settings.settings.first().healthExportEnabled) return
        runCatching {
            client().deleteRecords(type, recordIdsList = emptyList(), clientRecordIdsList = listOf(clientRecordId))
        }
    }

    private suspend fun import(granted: Set<String>) {
        val types = buildSet {
            if (HealthPermission.getReadPermission(WeightRecord::class) in granted) add(WeightRecord::class)
            if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) add(ExerciseSessionRecord::class)
        }
        if (types.isEmpty()) return
        val typeKey = changeTypeKey(types)
        val cursor = settings.healthChangeCursor()
        val consumed = cursor != null &&
            cursor.second == typeKey &&
            runCatching { consumeChanges(cursor.first, typeKey) }.getOrDefault(false)
        if (!consumed) initialImport(types, typeKey)
    }

    /** First import (or expired/invalid token): anchor changes, read history, then catch up. */
    private suspend fun initialImport(types: Set<KClass<out Record>>, typeKey: String) {
        val since = Instant.now().minus(30, ChronoUnit.DAYS)
        val client = client()
        val startToken = client.getChangesToken(ChangesTokenRequest(types))
        if (WeightRecord::class in types) {
            client.readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.after(since)))
                .records.forEach { upsertWeight(it) }
        }
        if (ExerciseSessionRecord::class in types) {
            client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.after(since)))
                .records.forEach { upsertWorkout(it) }
        }
        check(consumeChanges(startToken, typeKey)) {
            "Health Connect change token expired during initial import"
        }
    }

    /** Returns false when the token expired and a full re-import is needed. */
    private suspend fun consumeChanges(startToken: String, typeKey: String): Boolean {
        val client = client()
        var token = startToken
        while (true) {
            val response = client.getChanges(token)
            if (response.changesTokenExpired) return false
            response.changes.forEach { change ->
                when (change) {
                    is UpsertionChange -> when (val record = change.record) {
                        is WeightRecord -> upsertWeight(record)
                        is ExerciseSessionRecord -> upsertWorkout(record)
                        else -> Unit
                    }
                    is DeletionChange -> {
                        weightDao.deleteByHealthConnectId(change.recordId)
                        workoutDao.deleteByHealthConnectId(change.recordId)
                    }
                }
            }
            token = response.nextChangesToken
            if (!response.hasMore) {
                settings.setHealthChangeCursor(token, typeKey)
                return true
            }
        }
    }

    private fun changeTypeKey(types: Set<KClass<out Record>>): String = buildList {
        if (WeightRecord::class in types) add("weight")
        if (ExerciseSessionRecord::class in types) add("exercise")
    }.sorted().joinToString(",")

    private suspend fun upsertWeight(record: WeightRecord) {
        if (record.metadata.dataOrigin.packageName == context.packageName) return
        val existing = weightDao.getByHealthConnectId(record.metadata.id)
        val entry = WeightEntry(
            id = existing?.id ?: 0,
            timestampEpoch = record.time.toEpochMilli(),
            lastModifiedEpoch = System.currentTimeMillis(),
            weightKg = record.weight.inKilograms,
            note = existing?.note ?: "",
            healthConnectId = record.metadata.id,
        )
        if (existing == null) weightDao.insert(entry) else weightDao.update(entry)
    }

    private suspend fun upsertWorkout(record: ExerciseSessionRecord) {
        if (record.metadata.dataOrigin.packageName == context.packageName) return
        val existing = workoutDao.getByHealthConnectId(record.metadata.id)
        val startEpoch = record.startTime.toEpochMilli()
        val endEpoch = record.endTime.toEpochMilli()
        val session = WorkoutSession(
            id = existing?.id ?: 0,
            modeName = WorkoutMode.SIMPLE.name,
            startEpoch = startEpoch,
            endEpoch = endEpoch,
            totalSeconds = (endEpoch - startEpoch) / 1000,
            intervalSeconds = 0,
            exerciseSeconds = 0L,
            restSeconds = 0L,
            notes = existing?.notes ?: (record.title ?: ""),
            lastModifiedEpoch = System.currentTimeMillis(),
            healthConnectId = record.metadata.id,
        )
        if (existing == null) workoutDao.insert(session) else workoutDao.update(session)
    }

    // ponytail: bulk re-export of every local row each sync — idempotent via clientRecordId +
    // clientRecordVersion=lastModifiedEpoch, and it catches workouts finalized by the notification
    // service. Switch to per-event hooks if table sizes ever make this noticeable.
    private suspend fun export(granted: Set<String>) {
        val records = mutableListOf<Record>()
        if (HealthPermission.getWritePermission(WeightRecord::class) in granted) {
            weightDao.observeAll().first()
                .filter { it.healthConnectId == null }
                .mapTo(records) { entry ->
                    WeightRecord(
                        time = Instant.ofEpochMilli(entry.timestampEpoch),
                        zoneOffset = null,
                        weight = Mass.kilograms(entry.weightKg),
                        metadata = exportMetadata("sf-weight-${entry.id}", entry.lastModifiedEpoch),
                    )
                }
        }
        if (HealthPermission.getWritePermission(ExerciseSessionRecord::class) in granted) {
            workoutDao.observeAll().first()
                .filter { it.healthConnectId == null && it.endEpoch != null }
                .mapTo(records) { session ->
                    ExerciseSessionRecord(
                        startTime = Instant.ofEpochMilli(session.startEpoch),
                        startZoneOffset = null,
                        endTime = Instant.ofEpochMilli(session.endEpoch!!),
                        endZoneOffset = null,
                        exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
                        title = session.notes.ifBlank { null },
                        metadata = exportMetadata("sf-workout-${session.id}", session.lastModifiedEpoch),
                    )
                }
        }
        if (records.isNotEmpty()) client().insertRecords(records)
    }

    private fun exportMetadata(clientRecordId: String, version: Long): Metadata =
        Metadata.manualEntry(clientRecordId = clientRecordId, clientRecordVersion = version)

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}

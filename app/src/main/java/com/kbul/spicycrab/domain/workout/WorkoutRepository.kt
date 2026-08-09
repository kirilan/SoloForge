package com.kbul.spicycrab.domain.workout

import android.content.Context
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.domain.health.HealthConnectRepository
import com.kbul.spicycrab.notifications.WorkoutNotificationService
import com.kbul.spicycrab.notifications.tryStartForegroundService
import com.kbul.spicycrab.notifications.tryStartService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkoutRepository @Inject constructor(
    private val dao: WorkoutSessionDao,
    private val stateHolder: WorkoutStateHolder,
    private val healthConnect: HealthConnectRepository,
    @param:ApplicationContext private val context: Context,
) {
    private val startMutex = Mutex()

    fun observeAll(): Flow<List<WorkoutSession>> = dao.observeAll()
    // Read-only: the service owns the holder. Writing the reconciled value back from here raced
    // with it — a Room emission from before an insert still carries null, which would wipe the
    // state the service had just set, leaving its notification and wakelock reading an empty state.
    fun observeActive(): Flow<ActiveWorkoutState?> =
        combine(stateHolder.state, dao.observeActive()) { memory, dbActive ->
            reconcileActiveWorkoutState(memory, dbActive)
        }

    suspend fun resumeActiveNotification() {
        if (dao.getActive() == null) return
        context.tryStartForegroundService(
            WorkoutNotificationService.restoreIntent(context),
        )
    }

    suspend fun start(mode: WorkoutMode, intervalSeconds: Int): WorkoutSession = startMutex.withLock {
        dao.getActive()?.let { return@withLock it }
        val now = System.currentTimeMillis()
        val initialPhase = if (mode == WorkoutMode.EXERCISE_REST) {
            WorkoutPhase.PAUSED
        } else {
            WorkoutPhase.EXERCISE
        }
        val draft = WorkoutSession(
            modeName = mode.name,
            startEpoch = now,
            endEpoch = null,
            totalSeconds = 0L,
            intervalSeconds = if (mode == WorkoutMode.INTERVAL) intervalSeconds else 0,
            exerciseSeconds = 0L,
            restSeconds = 0L,
            notes = "",
            lastModifiedEpoch = now,
            activePhaseName = initialPhase.name,
            phaseStartEpoch = now,
        )
        val id = dao.insert(draft)
        val saved = draft.copy(id = id)
        context.tryStartForegroundService(
            WorkoutNotificationService.startIntent(context, id, mode, intervalSeconds, now),
        )
        saved
    }

    fun togglePhase() {
        context.tryStartService(WorkoutNotificationService.togglePhaseIntent(context))
    }

    fun togglePause() {
        context.tryStartService(WorkoutNotificationService.togglePauseIntent(context))
    }

    fun pause() {
        context.tryStartService(WorkoutNotificationService.pauseIntent(context))
    }

    suspend fun stop() {
        context.tryStartService(WorkoutNotificationService.stopIntent(context))
    }

    suspend fun update(updated: WorkoutSession) {
        dao.update(updated.copy(lastModifiedEpoch = System.currentTimeMillis()))
    }

    suspend fun delete(session: WorkoutSession) {
        dao.delete(session)
        healthConnect.onLocalWorkoutDeleted(session)
    }
}

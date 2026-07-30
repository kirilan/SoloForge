package com.kbul.spicycrab.domain.workout

import android.content.Context
import androidx.core.content.ContextCompat
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import com.kbul.spicycrab.domain.health.HealthConnectRepository
import com.kbul.spicycrab.notifications.WorkoutNotificationService
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
    fun observeActive(): Flow<ActiveWorkoutState?> =
        combine(stateHolder.state, dao.observeActive()) { memory, dbActive ->
            reconcileActiveWorkoutState(memory, dbActive).also { reconciled ->
                if (reconciled != memory) stateHolder.set(reconciled)
            }
        }

    suspend fun resumeActiveNotification() {
        if (dao.getActive() == null) return
        ContextCompat.startForegroundService(
            context,
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
        ContextCompat.startForegroundService(
            context,
            WorkoutNotificationService.startIntent(context, id, mode, intervalSeconds, now),
        )
        saved
    }

    fun togglePhase() {
        context.startService(WorkoutNotificationService.togglePhaseIntent(context))
    }

    fun togglePause() {
        context.startService(WorkoutNotificationService.togglePauseIntent(context))
    }

    fun pause() {
        context.startService(WorkoutNotificationService.pauseIntent(context))
    }

    suspend fun stop() {
        context.startService(WorkoutNotificationService.stopIntent(context))
    }

    suspend fun update(updated: WorkoutSession) {
        dao.update(updated.copy(lastModifiedEpoch = System.currentTimeMillis()))
    }

    suspend fun delete(session: WorkoutSession) {
        dao.delete(session)
        healthConnect.onLocalWorkoutDeleted(session)
    }
}

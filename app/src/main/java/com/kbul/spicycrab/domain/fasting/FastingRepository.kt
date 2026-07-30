package com.kbul.spicycrab.domain.fasting

import android.content.Context
import androidx.core.content.ContextCompat
import com.kbul.spicycrab.data.db.dao.FastSessionDao
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.notifications.FastingNotificationService
import com.kbul.spicycrab.notifications.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FastingRepository @Inject constructor(
    private val dao: FastSessionDao,
    private val reminderScheduler: ReminderScheduler,
    private val settings: SettingsRepo,
    @param:ApplicationContext private val context: Context,
) {
    private val startMutex = Mutex()

    fun observeActive(): Flow<FastSession?> = dao.observeActive()
    fun observeAll(): Flow<List<FastSession>> = dao.observeAll()
    suspend fun mostRecent(): FastSession? = dao.getMostRecent()
    suspend fun completedSessions(): List<FastSession> = dao.allCompleted()

    suspend fun resumeActiveNotification() {
        val active = dao.getActive() ?: return
        val mode = FastingMode.fromName(active.modeName)
        ContextCompat.startForegroundService(
            context,
            FastingNotificationService.startIntent(
                context,
                active.startEpoch,
                active.targetSeconds,
                mode.displayName,
            ),
        )
    }

    suspend fun startFast(mode: FastingMode): FastSession = startMutex.withLock {
        dao.getActive()?.let { return@withLock it }
        val now = System.currentTimeMillis()
        val session = FastSession(
            modeName = mode.name,
            targetSeconds = mode.fastSeconds,
            eatingWindowSeconds = mode.eatingWindowSeconds,
            startEpoch = now,
            endEpoch = null,
            completed = false,
        )
        val id = dao.insert(session)
        val saved = session.copy(id = id)

        ContextCompat.startForegroundService(
            context,
            FastingNotificationService.startIntent(context, now, mode.fastSeconds, mode.displayName),
        )
        syncReminders()
        saved
    }

    suspend fun stopFast(active: FastSession) = startMutex.withLock {
        val current = dao.getById(active.id) ?: return@withLock
        if (current.endEpoch != null) return@withLock
        val now = System.currentTimeMillis()
        val elapsed = (now - current.startEpoch) / 1000L
        val completed = elapsed >= current.targetSeconds
        dao.update(current.copy(endEpoch = now, completed = completed))

        context.startService(FastingNotificationService.stopIntent(context))
        syncReminders()
    }

    suspend fun updateSession(updated: FastSession): Boolean = startMutex.withLock {
        val existing = dao.getById(updated.id) ?: return@withLock false
        val now = System.currentTimeMillis()
        if (updated.startEpoch > now || updated.endEpoch?.let { it > now || it < updated.startEpoch } == true) {
            return@withLock false
        }
        if (updated.endEpoch == null) {
            val active = dao.getActive()
            if (active != null && active.id != updated.id) return@withLock false
        }
        val wasActive = existing.endEpoch == null
        val mode = FastingMode.fromName(updated.modeName)
        val end = updated.endEpoch
        val durationSec = if (end != null) (end - updated.startEpoch) / 1000L else 0L
        val completed = end != null && durationSec >= mode.fastSeconds
        dao.update(
            updated.copy(
                targetSeconds = mode.fastSeconds,
                eatingWindowSeconds = mode.eatingWindowSeconds,
                completed = completed,
            )
        )

        if (end == null) {
            ContextCompat.startForegroundService(
                context,
                FastingNotificationService.startIntent(
                    context, updated.startEpoch, mode.fastSeconds, mode.displayName,
                ),
            )
        } else {
            if (wasActive) {
                context.startService(FastingNotificationService.stopIntent(context))
            }
        }
        syncReminders()
        true
    }

    suspend fun deleteSession(session: FastSession) = startMutex.withLock {
        val existing = dao.getById(session.id) ?: return@withLock
        if (existing.endEpoch == null) {
            context.startService(FastingNotificationService.stopIntent(context))
        }
        dao.delete(existing)
        syncReminders()
    }

    suspend fun syncReminders() {
        reminderScheduler.cancelAlmostThere()
        reminderScheduler.cancelEatingWindowClosing()
        val currentSettings = settings.current()
        val active = dao.getActive()
        if (active != null) {
            if (currentSettings.almostThereEnabled) {
                reminderScheduler.scheduleAlmostThere(
                    active.startEpoch,
                    FastingMode.fromName(active.modeName),
                )
            }
            return
        }
        if (!currentSettings.eatingWindowClosingEnabled) return
        val mostRecent = dao.getMostRecentlyCompleted() ?: return
        val end = mostRecent.endEpoch ?: return
        reminderScheduler.scheduleEatingWindowClosing(end, FastingMode.fromName(mostRecent.modeName))
    }
}

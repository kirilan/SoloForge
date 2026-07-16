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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FastingRepository @Inject constructor(
    private val dao: FastSessionDao,
    private val reminderScheduler: ReminderScheduler,
    private val settings: SettingsRepo,
    @ApplicationContext private val context: Context,
) {

    fun observeActive(): Flow<FastSession?> = dao.observeActive()
    fun observeAll(): Flow<List<FastSession>> = dao.observeAll()
    suspend fun mostRecent(): FastSession? = dao.getMostRecent()
    suspend fun completedSessions(): List<FastSession> = dao.allCompleted()

    suspend fun startFast(mode: FastingMode): FastSession {
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
        if (settings.current().almostThereEnabled) {
            reminderScheduler.scheduleAlmostThere(now, mode)
        } else {
            reminderScheduler.cancelAlmostThere()
        }
        reminderScheduler.cancelEatingWindowClosing()
        return saved
    }

    suspend fun stopFast(active: FastSession) {
        val now = System.currentTimeMillis()
        val elapsed = (now - active.startEpoch) / 1000L
        val completed = elapsed >= active.targetSeconds
        dao.update(active.copy(endEpoch = now, completed = completed))

        context.startService(FastingNotificationService.stopIntent(context))
        reminderScheduler.cancelAlmostThere()

        if (completed && settings.current().eatingWindowClosingEnabled) {
            val mode = FastingMode.fromName(active.modeName)
            reminderScheduler.scheduleEatingWindowClosing(now, mode)
        }
    }

    suspend fun updateSession(updated: FastSession) {
        val mode = FastingMode.fromName(updated.modeName)
        val end = updated.endEpoch
        val durationSec = if (end != null) (end - updated.startEpoch) / 1000L else 0L
        val completed = end != null && durationSec >= updated.targetSeconds
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
            reminderScheduler.cancelAlmostThere()
            if (settings.current().almostThereEnabled) {
                reminderScheduler.scheduleAlmostThere(updated.startEpoch, mode)
            }
        }
    }

    suspend fun deleteSession(session: FastSession) {
        if (session.endEpoch == null) {
            context.startService(FastingNotificationService.stopIntent(context))
            reminderScheduler.cancelAlmostThere()
        }
        dao.delete(session)
    }
}

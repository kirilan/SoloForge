package com.kbul.spicycrab.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kbul.spicycrab.MainActivity
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.domain.workout.ActiveWorkoutState
import com.kbul.spicycrab.domain.workout.WorkoutMode
import com.kbul.spicycrab.domain.workout.WorkoutPhase
import com.kbul.spicycrab.domain.workout.WorkoutStateHolder
import com.kbul.spicycrab.domain.workout.activeSeconds
import com.kbul.spicycrab.domain.workout.chronometerBase
import com.kbul.spicycrab.domain.workout.currentPhaseElapsedSeconds
import com.kbul.spicycrab.domain.workout.toActiveWorkoutState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutNotificationService : Service() {

    @Inject lateinit var stateHolder: WorkoutStateHolder
    @Inject lateinit var dao: WorkoutSessionDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val persistenceMutex = Mutex()
    private var beepJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_RESTORE -> restoreActiveWorkout()
            ACTION_TOGGLE_PHASE -> togglePhase()
            ACTION_TOGGLE_PAUSE -> togglePause()
            ACTION_PAUSE -> setPhase(WorkoutPhase.PAUSED)
            ACTION_STOP -> {
                stopAndPersist()
                return START_NOT_STICKY
            }
            ACTION_DISCARD -> {
                finishService()
                return START_NOT_STICKY
            }
            else -> restoreActiveWorkout()
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val modeName = intent.getStringExtra(EXTRA_MODE) ?: WorkoutMode.SIMPLE.name
        val mode = WorkoutMode.fromName(modeName)
        val intervalSec = intent.getIntExtra(EXTRA_INTERVAL_SEC, 0)
        val startEpoch = intent.getLongExtra(EXTRA_START_EPOCH, System.currentTimeMillis())

        val initialPhase = when (mode) {
            WorkoutMode.SIMPLE, WorkoutMode.INTERVAL -> WorkoutPhase.EXERCISE
            WorkoutMode.EXERCISE_REST -> WorkoutPhase.PAUSED
        }

        val state = ActiveWorkoutState(
            sessionId = sessionId,
            mode = mode,
            startEpoch = startEpoch,
            intervalSeconds = intervalSec,
            phase = initialPhase,
            phaseStartEpoch = startEpoch,
            accumulatedExerciseSeconds = 0L,
            accumulatedRestSeconds = 0L,
        )
        stateHolder.set(state)

        startForegroundNotification(state)
        updateWakeLock(state)
        if (mode == WorkoutMode.INTERVAL && intervalSec > 0) {
            intervalBeep()  // confirmation beep so user knows audio works
            startBeepLoop()
        }
    }

    private fun togglePhase() {
        val cur = stateHolder.current() ?: return
        if (cur.mode != WorkoutMode.EXERCISE_REST) return
        val now = System.currentTimeMillis()
        val phaseElapsed = ((now - cur.phaseStartEpoch) / 1000L).coerceAtLeast(0L)
        val newPhase: WorkoutPhase
        var accEx = cur.accumulatedExerciseSeconds
        var accRest = cur.accumulatedRestSeconds
        when (cur.phase) {
            WorkoutPhase.EXERCISE -> { accEx += phaseElapsed; newPhase = WorkoutPhase.REST }
            WorkoutPhase.REST -> { accRest += phaseElapsed; newPhase = WorkoutPhase.EXERCISE }
            WorkoutPhase.PAUSED -> { newPhase = WorkoutPhase.EXERCISE }
        }
        setStateAndPersist(
            cur.copy(
                phase = newPhase,
                phaseStartEpoch = now,
                accumulatedExerciseSeconds = accEx,
                accumulatedRestSeconds = accRest,
            ),
        )
        phaseChangeFeedback()
    }

    private fun togglePause() {
        val cur = stateHolder.current() ?: return
        val now = System.currentTimeMillis()
        if (cur.phase == WorkoutPhase.PAUSED) {
            setStateAndPersist(cur.copy(phase = WorkoutPhase.EXERCISE, phaseStartEpoch = now))
        } else {
            val phaseElapsed = ((now - cur.phaseStartEpoch) / 1000L).coerceAtLeast(0L)
            val acc = when (cur.phase) {
                WorkoutPhase.EXERCISE -> cur.copy(accumulatedExerciseSeconds = cur.accumulatedExerciseSeconds + phaseElapsed)
                WorkoutPhase.REST -> cur.copy(accumulatedRestSeconds = cur.accumulatedRestSeconds + phaseElapsed)
                WorkoutPhase.PAUSED -> cur
            }
            setStateAndPersist(acc.copy(phase = WorkoutPhase.PAUSED, phaseStartEpoch = now))
        }
    }

    private fun setPhase(phase: WorkoutPhase) {
        val cur = stateHolder.current() ?: return
        if (cur.phase == phase) return
        val now = System.currentTimeMillis()
        val phaseElapsed = ((now - cur.phaseStartEpoch) / 1000L).coerceAtLeast(0L)
        val acc = when (cur.phase) {
            WorkoutPhase.EXERCISE -> cur.copy(accumulatedExerciseSeconds = cur.accumulatedExerciseSeconds + phaseElapsed)
            WorkoutPhase.REST -> cur.copy(accumulatedRestSeconds = cur.accumulatedRestSeconds + phaseElapsed)
            WorkoutPhase.PAUSED -> cur
        }
        setStateAndPersist(acc.copy(phase = phase, phaseStartEpoch = now))
    }

    private fun stopAndPersist() {
        scope.launch {
            val cur = stateHolder.current() ?: dao.getActive()?.toActiveWorkoutState()
            if (cur != null) {
                finalizeWorkout(cur)
            }
            finishService()
        }
    }

    private suspend fun finalizeWorkout(cur: ActiveWorkoutState) {
        persistenceMutex.withLock {
            val now = System.currentTimeMillis()
            val phaseElapsed = ((now - cur.phaseStartEpoch) / 1000L).coerceAtLeast(0L)
            val finalEx = cur.accumulatedExerciseSeconds +
                if (cur.phase == WorkoutPhase.EXERCISE) phaseElapsed else 0L
            val finalRest = cur.accumulatedRestSeconds +
                if (cur.phase == WorkoutPhase.REST) phaseElapsed else 0L
            val totalActive = cur.activeSeconds(now)
            val existing = dao.getById(cur.sessionId)
            if (existing != null && existing.endEpoch == null) {
                val finalized = existing.copy(
                    endEpoch = now,
                    totalSeconds = totalActive,
                    exerciseSeconds = finalEx,
                    restSeconds = finalRest,
                    lastModifiedEpoch = now,
                    activePhaseName = null,
                    phaseStartEpoch = null,
                )
                dao.update(finalized)
            }
        }
    }

    private fun setStateAndPersist(state: ActiveWorkoutState) {
        stateHolder.set(state)
        updateWakeLock(state)
        notifyTick(state)
        scope.launch {
            persistenceMutex.withLock {
                val existing = dao.getById(state.sessionId)
                if (existing != null && existing.endEpoch == null) {
                    dao.update(
                        existing.copy(
                            exerciseSeconds = state.accumulatedExerciseSeconds,
                            restSeconds = state.accumulatedRestSeconds,
                            lastModifiedEpoch = System.currentTimeMillis(),
                            activePhaseName = state.phase.name,
                            phaseStartEpoch = state.phaseStartEpoch,
                        )
                    )
                }
            }
        }
    }

    private fun restoreActiveWorkout() {
        scope.launch {
            val restored = stateHolder.current() ?: dao.getActive()?.toActiveWorkoutState()
            if (restored == null) {
                finishService()
                return@launch
            }
            stateHolder.set(restored)
            startForegroundNotification(restored)
            updateWakeLock(restored)
            if (restored.mode == WorkoutMode.INTERVAL && restored.intervalSeconds > 0) {
                startBeepLoop()
            }
        }
    }

    private fun finishService() {
        stateHolder.set(null)
        beepJob?.cancel()
        beepJob = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // Interval beeps have to land at an exact second while the screen is off, and nothing but a
    // wakelock can deliver that: allow-while-idle alarms are throttled to ~9 minutes in Doze, and
    // exact alarms need a permission Play only grants to alarm-clock apps.
    private fun updateWakeLock(cur: ActiveWorkoutState?) {
        val needed = cur != null &&
            cur.mode == WorkoutMode.INTERVAL &&
            cur.intervalSeconds > 0 &&
            cur.phase == WorkoutPhase.EXERCISE
        if (needed) acquireWakeLock() else releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            // ponytail: hard cap, so a lock we somehow fail to release can't drain the battery
            acquire(MAX_WAKE_LOCK_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    @SuppressLint("MissingPermission")
    private fun notifyTick(cur: ActiveWorkoutState?) {
        if (!canPostNotifications()) return
        androidx.core.app.NotificationManagerCompat.from(this)
            .notify(NOTIF_ID, buildNotification(cur))
    }

    private fun startBeepLoop() {
        beepJob?.cancel()
        beepJob = scope.launch {
            while (stateHolder.current()?.mode == WorkoutMode.INTERVAL) {
                val cur = stateHolder.current() ?: break
                val intervalMs = cur.intervalSeconds * 1000L
                if (intervalMs <= 0) break
                val activeMs = cur.activeSeconds(System.currentTimeMillis()) * 1000L
                val sinceLast = activeMs % intervalMs
                val waitMs = (intervalMs - sinceLast).coerceAtLeast(50L)
                delay(waitMs)
                if (stateHolder.current()?.phase == WorkoutPhase.EXERCISE) intervalBeep()
            }
        }
    }

    private fun intervalBeep() {
        when (ringerMode()) {
            AudioManager.RINGER_MODE_NORMAL -> playTone(volume = 80, tone = ToneGenerator.TONE_CDMA_HIGH_L, durationMs = 500)
            AudioManager.RINGER_MODE_VIBRATE -> vibrateOnce(250L)
            AudioManager.RINGER_MODE_SILENT -> { /* respect silent */ }
        }
    }

    private fun phaseChangeFeedback() {
        when (ringerMode()) {
            AudioManager.RINGER_MODE_NORMAL -> {
                vibrateOnce(60L)
                playTone(volume = 35, tone = ToneGenerator.TONE_PROP_ACK, durationMs = 120)
            }
            AudioManager.RINGER_MODE_VIBRATE -> vibrateOnce(60L)
            AudioManager.RINGER_MODE_SILENT -> { /* respect silent */ }
        }
    }

    private fun ringerMode(): Int =
        getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun vibrateOnce(durationMs: Long) {
        runCatching {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun playTone(volume: Int, tone: Int, durationMs: Int) {
        scope.launch {
            runCatching {
                val tg = ToneGenerator(AudioManager.STREAM_MUSIC, volume)
                tg.startTone(tone, durationMs)
                delay(durationMs.toLong() + 200L)
                tg.release()
            }
        }
    }

    private fun startForegroundNotification(cur: ActiveWorkoutState?) {
        if (!tryStartForeground(NOTIF_ID, buildNotification(cur))) stopSelf()
    }

    // While the workout runs, SystemUI owns the clock: a chronometer based on the accumulated
    // active time keeps counting through deep sleep, where a ticker of ours would not. Paused time
    // must not count, so a paused workout drops the chronometer and shows a frozen total instead.
    // The state is passed in, never read from the shared holder: the UI's reconciliation writes to
    // that holder from another thread, and a build that lost the race would render the empty
    // fallback and — with no ticker to repaint it — stay wrong for the whole workout.
    private fun buildNotification(cur: ActiveWorkoutState?): Notification {
        val title = if (cur != null) {
            getString(R.string.notif_workout_title, getString(cur.mode.labelRes))
        } else {
            getString(R.string.notif_workout_title_plain)
        }
        val phaseLabel = cur?.let {
            getString(
                when (it.phase) {
                    WorkoutPhase.EXERCISE -> R.string.workout_working
                    WorkoutPhase.REST -> R.string.workout_resting
                    WorkoutPhase.PAUSED -> R.string.workout_paused
                }
            )
        }
        val now = System.currentTimeMillis()
        val chronometerBase = cur?.chronometerBase(now)
        val text = when {
            cur == null -> getString(R.string.notif_workout_active)
            chronometerBase != null -> phaseLabel!!
            else -> "${formatHms(cur.activeSeconds(now))} · $phaseLabel"
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NotificationChannels.ACTIVE_WORKOUT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setWhen(chronometerBase ?: now)
            .setShowWhen(chronometerBase != null)
            .setUsesChronometer(chronometerBase != null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        beepJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1002
        private const val WAKE_LOCK_TAG = "SoloForge:interval-beeps"
        private const val MAX_WAKE_LOCK_MS = 4 * 60 * 60 * 1000L
        const val ACTION_START = "com.kbul.spicycrab.action.START_WORKOUT"
        const val ACTION_RESTORE = "com.kbul.spicycrab.action.RESTORE_WORKOUT"
        const val ACTION_TOGGLE_PHASE = "com.kbul.spicycrab.action.TOGGLE_PHASE"
        const val ACTION_TOGGLE_PAUSE = "com.kbul.spicycrab.action.TOGGLE_PAUSE"
        const val ACTION_PAUSE = "com.kbul.spicycrab.action.PAUSE_WORKOUT"
        const val ACTION_STOP = "com.kbul.spicycrab.action.STOP_WORKOUT"
        const val ACTION_DISCARD = "com.kbul.spicycrab.action.DISCARD_WORKOUT"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_MODE = "mode"
        const val EXTRA_INTERVAL_SEC = "interval_sec"
        const val EXTRA_START_EPOCH = "start_epoch"

        fun startIntent(
            context: Context,
            sessionId: Long,
            mode: WorkoutMode,
            intervalSec: Int,
            startEpoch: Long,
        ): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_INTERVAL_SEC, intervalSec)
                putExtra(EXTRA_START_EPOCH, startEpoch)
            }

        fun togglePhaseIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_TOGGLE_PHASE }

        fun restoreIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_RESTORE }

        fun togglePauseIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_TOGGLE_PAUSE }

        fun pauseIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_PAUSE }

        fun stopIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_STOP }

        fun discardIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_DISCARD }

        private fun formatHms(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }
    }
}

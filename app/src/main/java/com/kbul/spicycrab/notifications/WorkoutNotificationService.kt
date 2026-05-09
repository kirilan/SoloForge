package com.kbul.spicycrab.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kbul.spicycrab.MainActivity
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.csv.CsvExporter
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.workout.ActiveWorkoutState
import com.kbul.spicycrab.domain.workout.WorkoutMode
import com.kbul.spicycrab.domain.workout.WorkoutPhase
import com.kbul.spicycrab.domain.workout.WorkoutStateHolder
import com.kbul.spicycrab.domain.workout.activeSeconds
import com.kbul.spicycrab.domain.workout.currentPhaseElapsedSeconds
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class WorkoutNotificationService : Service() {

    @Inject lateinit var stateHolder: WorkoutStateHolder
    @Inject lateinit var dao: WorkoutSessionDao
    @Inject lateinit var settings: SettingsRepo
    @Inject lateinit var csvExporter: CsvExporter

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private var beepJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_TOGGLE_PHASE -> togglePhase()
            ACTION_TOGGLE_PAUSE -> togglePause()
            ACTION_PAUSE -> setPhase(WorkoutPhase.PAUSED)
            ACTION_STOP -> {
                stopAndPersist()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val modeName = intent.getStringExtra(EXTRA_MODE) ?: WorkoutMode.SIMPLE.name
        val mode = WorkoutMode.fromName(modeName)
        val intervalSec = intent.getIntExtra(EXTRA_INTERVAL_SEC, 0)
        val now = System.currentTimeMillis()

        val initialPhase = when (mode) {
            WorkoutMode.SIMPLE, WorkoutMode.INTERVAL -> WorkoutPhase.EXERCISE
            WorkoutMode.EXERCISE_REST -> WorkoutPhase.PAUSED
        }

        stateHolder.set(
            ActiveWorkoutState(
                sessionId = sessionId,
                mode = mode,
                startEpoch = now,
                intervalSeconds = intervalSec,
                phase = initialPhase,
                phaseStartEpoch = now,
                accumulatedExerciseSeconds = 0L,
                accumulatedRestSeconds = 0L,
            )
        )

        startForegroundNotification()
        startTicker()
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
        stateHolder.set(
            cur.copy(
                phase = newPhase,
                phaseStartEpoch = now,
                accumulatedExerciseSeconds = accEx,
                accumulatedRestSeconds = accRest,
            )
        )
        phaseChangeFeedback()
    }

    private fun togglePause() {
        val cur = stateHolder.current() ?: return
        val now = System.currentTimeMillis()
        if (cur.phase == WorkoutPhase.PAUSED) {
            stateHolder.set(cur.copy(phase = WorkoutPhase.EXERCISE, phaseStartEpoch = now))
        } else {
            val phaseElapsed = ((now - cur.phaseStartEpoch) / 1000L).coerceAtLeast(0L)
            val acc = when (cur.phase) {
                WorkoutPhase.EXERCISE -> cur.copy(accumulatedExerciseSeconds = cur.accumulatedExerciseSeconds + phaseElapsed)
                WorkoutPhase.REST -> cur.copy(accumulatedRestSeconds = cur.accumulatedRestSeconds + phaseElapsed)
                WorkoutPhase.PAUSED -> cur
            }
            stateHolder.set(acc.copy(phase = WorkoutPhase.PAUSED, phaseStartEpoch = now))
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
        stateHolder.set(acc.copy(phase = phase, phaseStartEpoch = now))
    }

    private fun stopAndPersist() {
        val cur = stateHolder.current()
        if (cur != null) {
            val now = System.currentTimeMillis()
            val phaseElapsed = ((now - cur.phaseStartEpoch) / 1000L).coerceAtLeast(0L)
            val finalEx = cur.accumulatedExerciseSeconds +
                if (cur.phase == WorkoutPhase.EXERCISE) phaseElapsed else 0L
            val finalRest = cur.accumulatedRestSeconds +
                if (cur.phase == WorkoutPhase.REST) phaseElapsed else 0L
            val totalActive = cur.activeSeconds(now)
            scope.launch {
                val existing = dao.getById(cur.sessionId)
                if (existing != null) {
                    val finalized = existing.copy(
                        endEpoch = now,
                        totalSeconds = totalActive,
                        exerciseSeconds = finalEx,
                        restSeconds = finalRest,
                        lastModifiedEpoch = now,
                    )
                    dao.update(finalized)
                    settings.current().exportFolderUri?.let { uriStr ->
                        csvExporter.appendWorkoutEntry(Uri.parse(uriStr), finalized)
                    }
                }
                stateHolder.set(null)
                tickerJob?.cancel(); tickerJob = null
                beepJob?.cancel(); beepJob = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            stateHolder.set(null)
            tickerJob?.cancel(); tickerJob = null
            beepJob?.cancel(); beepJob = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (stateHolder.current() != null) {
                runCatching { notifyTick() }
                delay(1_000L)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyTick() {
        if (!canPostNotifications()) return
        androidx.core.app.NotificationManagerCompat.from(this)
            .notify(NOTIF_ID, buildNotification())
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

    private fun startForegroundNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIF_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val cur = stateHolder.current()
        val title = if (cur != null) "Workout · ${cur.mode.displayName}" else "Workout"
        val text = if (cur != null) {
            val totalSec = cur.activeSeconds(System.currentTimeMillis())
            val phaseLabel = when (cur.phase) {
                WorkoutPhase.EXERCISE -> "Working"
                WorkoutPhase.REST -> "Resting"
                WorkoutPhase.PAUSED -> "Paused"
            }
            "${formatHms(totalSec)} · $phaseLabel"
        } else "Active"

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
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        beepJob?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1002
        const val ACTION_START = "com.kbul.spicycrab.action.START_WORKOUT"
        const val ACTION_TOGGLE_PHASE = "com.kbul.spicycrab.action.TOGGLE_PHASE"
        const val ACTION_TOGGLE_PAUSE = "com.kbul.spicycrab.action.TOGGLE_PAUSE"
        const val ACTION_PAUSE = "com.kbul.spicycrab.action.PAUSE_WORKOUT"
        const val ACTION_STOP = "com.kbul.spicycrab.action.STOP_WORKOUT"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_MODE = "mode"
        const val EXTRA_INTERVAL_SEC = "interval_sec"

        fun startIntent(context: Context, sessionId: Long, mode: WorkoutMode, intervalSec: Int): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_INTERVAL_SEC, intervalSec)
            }

        fun togglePhaseIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_TOGGLE_PHASE }

        fun togglePauseIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_TOGGLE_PAUSE }

        fun pauseIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_PAUSE }

        fun stopIntent(context: Context): Intent =
            Intent(context, WorkoutNotificationService::class.java).apply { action = ACTION_STOP }

        private fun formatHms(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }
    }
}

package com.kbul.spicycrab.notifications

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import com.kbul.spicycrab.MainActivity
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.dao.FastSessionDao
import com.kbul.spicycrab.domain.fasting.FastingMode
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

@AndroidEntryPoint
class FastingNotificationService : Service() {

    @Inject lateinit var dao: FastSessionDao

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var startEpoch: Long = 0L
    private var targetSeconds: Long = 0L
    private var modeName: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startFast(intent)
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> restoreActiveFast()
        }
        return START_STICKY
    }

    private fun restoreActiveFast() {
        scope.launch {
            val active = dao.getActive()
            if (active == null) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@launch
            }
            startFast(
                startEpoch = active.startEpoch,
                targetSeconds = active.targetSeconds,
                modeDisplay = FastingMode.fromName(active.modeName).displayName,
            )
        }
    }

    private fun startFast(intent: Intent) {
        startFast(
            startEpoch = intent.getLongExtra(EXTRA_START_EPOCH, System.currentTimeMillis()),
            targetSeconds = intent.getLongExtra(EXTRA_TARGET_SECONDS, 0L),
            modeDisplay = intent.getStringExtra(EXTRA_MODE_DISPLAY) ?: "",
        )
    }

    private fun startFast(startEpoch: Long, targetSeconds: Long, modeDisplay: String) {
        this.startEpoch = startEpoch
        this.targetSeconds = targetSeconds
        modeName = modeDisplay

        if (!tryStartForeground(NOTIF_ID, buildNotification())) stopSelf()
    }

    // The notification never needs repainting: SystemUI advances the chronometer itself, which is
    // the only thing that keeps counting while the device is in deep sleep.
    private fun buildNotification(): Notification {
        val targetEnd = startEpoch + targetSeconds * 1000L
        val targetTime = DateFormat.getTimeFormat(this).format(Date(targetEnd))

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, NotificationChannels.ACTIVE_FAST)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_fasting_title, modeName))
            .setContentText(getString(R.string.notif_fasting_target, targetTime))
            .setWhen(startEpoch)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        const val ACTION_START = "com.kbul.spicycrab.action.START_FAST"
        const val ACTION_STOP = "com.kbul.spicycrab.action.STOP_FAST"
        const val EXTRA_START_EPOCH = "start_epoch"
        const val EXTRA_TARGET_SECONDS = "target_seconds"
        const val EXTRA_MODE_DISPLAY = "mode_display"

        fun startIntent(
            context: Context,
            startEpoch: Long,
            targetSeconds: Long,
            modeDisplay: String,
        ): Intent = Intent(context, FastingNotificationService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_START_EPOCH, startEpoch)
            putExtra(EXTRA_TARGET_SECONDS, targetSeconds)
            putExtra(EXTRA_MODE_DISPLAY, modeDisplay)
        }

        fun stopIntent(context: Context): Intent =
            Intent(context, FastingNotificationService::class.java).apply { action = ACTION_STOP }
    }
}

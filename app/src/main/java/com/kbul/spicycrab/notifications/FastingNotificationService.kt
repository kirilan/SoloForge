package com.kbul.spicycrab.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kbul.spicycrab.MainActivity
import com.kbul.spicycrab.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FastingNotificationService : Service() {

    private var tickerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

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
        }
        return START_STICKY
    }

    private fun startFast(intent: Intent) {
        startEpoch = intent.getLongExtra(EXTRA_START_EPOCH, System.currentTimeMillis())
        targetSeconds = intent.getLongExtra(EXTRA_TARGET_SECONDS, 0L)
        modeName = intent.getStringExtra(EXTRA_MODE_DISPLAY) ?: ""

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

        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(30_000L)
                runCatching { notifyTick() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyTick() {
        if (!canPostNotifications()) return
        androidx.core.app.NotificationManagerCompat
            .from(this)
            .notify(NOTIF_ID, buildNotification())
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val elapsedSec = ((now - startEpoch) / 1000L).coerceAtLeast(0L)
        val remainingSec = (targetSeconds - elapsedSec).coerceAtLeast(0L)
        val text = getString(R.string.notif_fasting_text, formatHms(elapsedSec), formatHms(remainingSec))

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
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
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

        private fun formatHms(seconds: Long): String {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            val s = seconds % 60
            return "%02d:%02d:%02d".format(h, m, s)
        }
    }
}

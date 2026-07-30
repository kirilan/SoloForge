package com.kbul.spicycrab.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kbul.spicycrab.MainActivity
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.dao.FastSessionDao
import com.kbul.spicycrab.data.prefs.SettingsRepo
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class FastingReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val dao: FastSessionDao,
    private val settings: SettingsRepo,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val kind = inputData.getString(KEY_KIND) ?: return Result.success()
        val referenceEpoch = inputData.getLong(KEY_REFERENCE_EPOCH, Long.MIN_VALUE)
        if (referenceEpoch == Long.MIN_VALUE) return Result.success()
        val currentSettings = settings.current()
        val notification = when (kind) {
            KIND_ALMOST_THERE -> {
                val active = dao.getActive()
                if (!currentSettings.almostThereEnabled || active?.startEpoch != referenceEpoch) {
                    return Result.success()
                }
                Triple(
                    applicationContext.getString(R.string.reminder_almost_there_title),
                    applicationContext.resources.getStringArray(R.array.fasting_encouragements).random(),
                    NOTIF_ID_ALMOST_THERE,
                )
            }
            KIND_EATING_WINDOW -> {
                val latestEnd = dao.getMostRecentlyCompleted()?.endEpoch
                if (
                    !currentSettings.eatingWindowClosingEnabled ||
                    dao.getActive() != null ||
                    latestEnd != referenceEpoch
                ) {
                    return Result.success()
                }
                Triple(
                    applicationContext.getString(R.string.reminder_window_title),
                    applicationContext.getString(R.string.reminder_window_message),
                    NOTIF_ID_WINDOW_CLOSING,
                )
            }
            else -> return Result.success()
        }
        showNotification(notification.first, notification.second, notification.third)
        return Result.success()
    }

    private fun showNotification(title: String, message: String, id: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.FASTING_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(id, notif)
    }

    companion object {
        const val KEY_KIND = "kind"
        const val KEY_REFERENCE_EPOCH = "reference_epoch"
        const val KIND_ALMOST_THERE = "almost_there"
        const val KIND_EATING_WINDOW = "eating_window"
        private const val NOTIF_ID_ALMOST_THERE = 2001
        private const val NOTIF_ID_WINDOW_CLOSING = 2002
    }
}

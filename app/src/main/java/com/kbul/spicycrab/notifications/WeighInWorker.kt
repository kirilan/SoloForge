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
import com.kbul.spicycrab.data.prefs.SettingsRepo
import androidx.hilt.work.HiltWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.DayOfWeek
import java.time.LocalTime

@HiltWorker
class WeighInWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settings: SettingsRepo,
    private val reminderScheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val day = inputData.getInt(KEY_DAY_OF_WEEK, DayOfWeek.MONDAY.value)
            .coerceIn(DayOfWeek.MONDAY.value, DayOfWeek.SUNDAY.value)
        val hour = inputData.getInt(KEY_HOUR, 8).coerceIn(0, 23)
        val minute = inputData.getInt(KEY_MINUTE, 0).coerceIn(0, 59)
        val current = settings.current()
        if (
            !current.weighInEnabled ||
            current.weighInDayOfWeek != day ||
            current.weighInHour != hour ||
            current.weighInMinute != minute
        ) {
            return Result.success()
        }
        reminderScheduler.scheduleNextWeeklyWeighIn(
            DayOfWeek.of(day),
            LocalTime.of(hour, minute),
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notif = NotificationCompat.Builder(applicationContext, NotificationChannels.WEIGHT_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.weigh_in_title))
            .setContentText(applicationContext.getString(R.string.weigh_in_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notif)
        return Result.success()
    }

    companion object {
        const val NOTIF_ID = 3001
        const val KEY_DAY_OF_WEEK = "day_of_week"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
    }
}

package com.kbul.spicycrab.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import com.kbul.spicycrab.domain.fasting.FastingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wm get() = WorkManager.getInstance(context)

    fun scheduleAlmostThere(fastStartEpoch: Long, mode: FastingMode) {
        val targetEnd = fastStartEpoch + mode.fastSeconds * 1000L
        val fireAt = targetEnd - TimeUnit.HOURS.toMillis(1)
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) return

        val message = ENCOURAGEMENTS.random()
        val data = Data.Builder()
            .putString(FastingReminderWorker.KEY_TITLE, "Almost there")
            .putString(FastingReminderWorker.KEY_MESSAGE, message)
            .putInt(FastingReminderWorker.KEY_NOTIFICATION_ID, NOTIF_ID_ALMOST_THERE)
            .build()

        val req = OneTimeWorkRequestBuilder<FastingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(TAG_ALMOST_THERE)
            .build()

        wm.enqueueUniqueWork(WORK_NAME_ALMOST_THERE, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelAlmostThere() {
        wm.cancelUniqueWork(WORK_NAME_ALMOST_THERE)
    }

    fun scheduleEatingWindowClosing(fastEndEpoch: Long, mode: FastingMode) {
        val windowClose = fastEndEpoch + mode.eatingWindowSeconds * 1000L
        val fireAt = windowClose - TimeUnit.HOURS.toMillis(1)
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) return

        val data = Data.Builder()
            .putString(FastingReminderWorker.KEY_TITLE, "Eating window closing")
            .putString(
                FastingReminderWorker.KEY_MESSAGE,
                "Your eating window closes in 1 hour. Plan your last meal."
            )
            .putInt(FastingReminderWorker.KEY_NOTIFICATION_ID, NOTIF_ID_WINDOW_CLOSING)
            .build()

        val req = OneTimeWorkRequestBuilder<FastingReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(TAG_WINDOW_CLOSING)
            .build()

        wm.enqueueUniqueWork(WORK_NAME_WINDOW_CLOSING, ExistingWorkPolicy.REPLACE, req)
    }

    fun cancelEatingWindowClosing() {
        wm.cancelUniqueWork(WORK_NAME_WINDOW_CLOSING)
    }

    fun scheduleWeeklyWeighIn(dayOfWeek: DayOfWeek, time: LocalTime) {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var first = LocalDate.now(zone).with(dayOfWeek).atTime(time)
        if (!first.isAfter(now)) first = first.plusWeeks(1)
        val initialDelay = java.time.Duration.between(now, first).toMillis()

        val req = PeriodicWorkRequestBuilder<WeighInWorker>(7, TimeUnit.DAYS)
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME_WEEKLY_WEIGH_IN,
            ExistingPeriodicWorkPolicy.UPDATE,
            req,
        )
    }

    fun cancelWeeklyWeighIn() {
        wm.cancelUniqueWork(WORK_NAME_WEEKLY_WEIGH_IN)
    }

    companion object {
        private const val WORK_NAME_ALMOST_THERE = "fasting_almost_there"
        private const val WORK_NAME_WINDOW_CLOSING = "eating_window_closing"
        private const val WORK_NAME_WEEKLY_WEIGH_IN = "weekly_weigh_in"
        private const val TAG_ALMOST_THERE = "tag_almost_there"
        private const val TAG_WINDOW_CLOSING = "tag_window_closing"
        private const val NOTIF_ID_ALMOST_THERE = 2001
        private const val NOTIF_ID_WINDOW_CLOSING = 2002

        private val ENCOURAGEMENTS = listOf(
            "One hour to go — you've got this.",
            "60 minutes left. Strong finish.",
            "Final stretch! Stay hydrated and keep going.",
            "Almost there. Your future self will thank you.",
            "One more hour. Don't break the chain.",
        )
    }
}

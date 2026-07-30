package com.kbul.spicycrab.notifications

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import com.kbul.spicycrab.domain.fasting.FastingMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val wm get() = WorkManager.getInstance(context)

    fun scheduleAlmostThere(fastStartEpoch: Long, mode: FastingMode) {
        val targetEnd = fastStartEpoch + mode.fastSeconds * 1000L
        val fireAt = targetEnd - TimeUnit.HOURS.toMillis(1)
        val delay = fireAt - System.currentTimeMillis()
        if (delay <= 0) return

        val data = Data.Builder()
            .putString(FastingReminderWorker.KEY_KIND, FastingReminderWorker.KIND_ALMOST_THERE)
            .putLong(FastingReminderWorker.KEY_REFERENCE_EPOCH, fastStartEpoch)
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
            .putString(FastingReminderWorker.KEY_KIND, FastingReminderWorker.KIND_EATING_WINDOW)
            .putLong(FastingReminderWorker.KEY_REFERENCE_EPOCH, fastEndEpoch)
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
        enqueueWeeklyWeighIn(dayOfWeek, time, ExistingWorkPolicy.REPLACE)
    }

    fun scheduleNextWeeklyWeighIn(dayOfWeek: DayOfWeek, time: LocalTime) {
        enqueueWeeklyWeighIn(dayOfWeek, time, ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    private fun enqueueWeeklyWeighIn(
        dayOfWeek: DayOfWeek,
        time: LocalTime,
        policy: ExistingWorkPolicy,
    ) {
        val zone = ZoneId.systemDefault()
        val now = ZonedDateTime.now(zone)
        var first = now.toLocalDate().with(dayOfWeek).atTime(time).atZone(zone)
        if (!first.isAfter(now)) first = first.plusWeeks(1)
        val initialDelay = java.time.Duration.between(now.toInstant(), first.toInstant()).toMillis()

        val data = Data.Builder()
            .putInt(WeighInWorker.KEY_DAY_OF_WEEK, dayOfWeek.value)
            .putInt(WeighInWorker.KEY_HOUR, time.hour)
            .putInt(WeighInWorker.KEY_MINUTE, time.minute)
            .build()
        val req = OneTimeWorkRequestBuilder<WeighInWorker>()
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .addTag(TAG_WEEKLY_WEIGH_IN)
            .build()
        wm.enqueueUniqueWork(WORK_NAME_WEEKLY_WEIGH_IN, policy, req)
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
        private const val TAG_WEEKLY_WEIGH_IN = "tag_weekly_weigh_in"
    }
}

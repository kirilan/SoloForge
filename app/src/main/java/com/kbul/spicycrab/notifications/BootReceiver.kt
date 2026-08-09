package com.kbul.spicycrab.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kbul.spicycrab.domain.fasting.FastingRepository
import com.kbul.spicycrab.domain.workout.WorkoutRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restores the live timers after a reboot, and repaints them after a timezone change so the fasting
 * notification's goal time stays honest. Both repositories no-op when nothing is active, and
 * WorkManager restores its own reminder jobs, so there is nothing else to do here.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var fastingRepository: FastingRepository
    @Inject lateinit var workoutRepository: WorkoutRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                fastingRepository.resumeActiveNotification()
                workoutRepository.resumeActiveNotification()
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIMEZONE_CHANGED,
        )
    }
}

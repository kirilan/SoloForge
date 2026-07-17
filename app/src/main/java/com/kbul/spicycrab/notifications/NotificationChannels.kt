package com.kbul.spicycrab.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import com.kbul.spicycrab.R

object NotificationChannels {
    const val ACTIVE_FAST = "active_fast"
    const val ACTIVE_WORKOUT = "active_workout"
    const val FASTING_REMINDERS = "fasting_reminders"
    const val WEIGHT_REMINDERS = "weight_reminders"

    fun ensureCreated(context: Context) {
        val nm = context.getSystemService<NotificationManager>() ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                ACTIVE_FAST,
                context.getString(R.string.channel_active_fast),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_active_fast_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ACTIVE_WORKOUT,
                context.getString(R.string.channel_active_workout),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.channel_active_workout_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                FASTING_REMINDERS,
                context.getString(R.string.channel_fasting_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_fasting_reminders_desc) }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                WEIGHT_REMINDERS,
                context.getString(R.string.channel_weight_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_weight_reminders_desc) }
        )
    }
}

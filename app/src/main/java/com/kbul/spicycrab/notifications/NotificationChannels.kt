package com.kbul.spicycrab.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

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
                "Active fast timer",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Persistent notification while a fast is running" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                ACTIVE_WORKOUT,
                "Active workout timer",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Persistent notification while a workout is running" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                FASTING_REMINDERS,
                "Fasting reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Eating-window-closing alerts and almost-there encouragement" }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                WEIGHT_REMINDERS,
                "Weight reminders",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Weekly weigh-in reminder" }
        )
    }
}

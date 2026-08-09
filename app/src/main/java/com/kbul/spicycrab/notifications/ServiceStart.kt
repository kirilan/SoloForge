package com.kbul.spicycrab.notifications

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat

// Android 12+ rejects service starts from the background, and the app can slip into the background
// between our request and the service running. Losing the notification is recoverable —
// MainActivity.onStart reconciles both services — so never let it take the process down.
// ForegroundServiceStartNotAllowedException is API 31+; catching its IllegalStateException
// superclass keeps this one clause valid all the way down to API 26.

fun Context.tryStartForegroundService(intent: Intent) {
    try {
        ContextCompat.startForegroundService(this, intent)
    } catch (_: IllegalStateException) {
    }
}

fun Context.tryStartService(intent: Intent) {
    try {
        startService(intent)
    } catch (_: IllegalStateException) {
    }
}

fun Service.tryStartForeground(id: Int, notification: Notification): Boolean = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
        startForeground(id, notification)
    }
    true
} catch (_: IllegalStateException) {
    false
}

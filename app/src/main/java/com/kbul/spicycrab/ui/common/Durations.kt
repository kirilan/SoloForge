package com.kbul.spicycrab.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.kbul.spicycrab.R

/** `04:13:22`, or `2d 04:13:22` once a day has passed — a 7-day fast is unreadable as `168:00:00`. */
@Composable
fun formatHms(seconds: Long): String {
    val days = seconds / 86_400
    val rest = seconds % 86_400
    val hms = "%02d:%02d:%02d".format(rest / 3600, (rest % 3600) / 60, rest % 60)
    return if (days > 0) stringResource(R.string.duration_days_hms, days.toInt(), hms) else hms
}

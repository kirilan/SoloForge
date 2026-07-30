package com.kbul.spicycrab.domain.workout

import androidx.annotation.StringRes
import com.kbul.spicycrab.R

enum class WorkoutMode(@param:StringRes val labelRes: Int) {
    SIMPLE(R.string.workout_mode_simple),
    INTERVAL(R.string.workout_mode_interval),
    EXERCISE_REST(R.string.workout_mode_exercise_rest);

    val requiresScreenOn: Boolean get() = this != SIMPLE

    companion object {
        fun fromName(name: String?): WorkoutMode =
            entries.firstOrNull { it.name == name } ?: SIMPLE
    }
}

enum class WorkoutPhase {
    PAUSED,
    EXERCISE,
    REST,
}

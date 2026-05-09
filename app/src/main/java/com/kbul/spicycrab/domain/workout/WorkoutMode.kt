package com.kbul.spicycrab.domain.workout

enum class WorkoutMode(val displayName: String) {
    SIMPLE("Simple"),
    INTERVAL("Interval"),
    EXERCISE_REST("Exercise & Rest");

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

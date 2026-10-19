package com.kbul.spicycrab.domain.fasting

enum class FastingMode(
    val displayName: String,
    val fastHours: Int,
    val eatingWindowHours: Int,
) {
    TWELVE_TWELVE("12:12", 12, 12),
    FOURTEEN_TEN("14:10", 14, 10),
    SIXTEEN_EIGHT("16:8", 16, 8),
    EIGHTEEN_SIX("18:6", 18, 6),
    TWENTY_FOUR("20:4", 20, 4),
    THIRTY_SIX_HOUR("36h", 36, 12),
    CUSTOM("", 0, 0);

    fun targetSeconds(customHours: Int): Long =
        if (this == CUSTOM) customHours.coerceIn(1, MAX_CUSTOM_HOURS) * 3600L else fastHours * 3600L

    // A custom fast of a day or more has no eating window, so no "window closing" reminder follows it.
    fun eatingWindowSeconds(customHours: Int): Long =
        if (this == CUSTOM) (24 - customHours).coerceAtLeast(0) * 3600L else eatingWindowHours * 3600L

    companion object {
        const val MAX_CUSTOM_HOURS = 7 * 24
        const val DEFAULT_CUSTOM_HOURS = 48

        fun fromName(name: String?): FastingMode =
            entries.firstOrNull { it.name == name } ?: SIXTEEN_EIGHT

        /** Presets are named by their ratio; a custom fast by its own target, since that's all it has. */
        fun label(modeName: String, targetSeconds: Long): String {
            val mode = fromName(modeName)
            return if (mode == CUSTOM) "${targetSeconds / 3600}h" else mode.displayName
        }
    }
}

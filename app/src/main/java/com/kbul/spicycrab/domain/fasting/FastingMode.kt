package com.kbul.spicycrab.domain.fasting

enum class FastingMode(
    val displayName: String,
    val fastHours: Int,
    val eatingWindowHours: Int,
) {
    SIXTEEN_EIGHT("16:8", 16, 8),
    EIGHTEEN_SIX("18:6", 18, 6),
    TWENTY_FOUR("20:4", 20, 4),
    THIRTY_SIX_HOUR("36h", 36, 12);

    val fastSeconds: Long get() = fastHours * 3600L
    val eatingWindowSeconds: Long get() = eatingWindowHours * 3600L

    companion object {
        fun fromName(name: String?): FastingMode =
            entries.firstOrNull { it.name == name } ?: SIXTEEN_EIGHT
    }
}

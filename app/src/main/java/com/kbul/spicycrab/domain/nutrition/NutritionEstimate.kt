package com.kbul.spicycrab.domain.nutrition

data class NutritionEstimate(
    val itemName: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
    val confidence: String,
    val notes: String,
)

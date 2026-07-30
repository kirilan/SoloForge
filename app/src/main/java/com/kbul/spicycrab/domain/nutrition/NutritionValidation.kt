package com.kbul.spicycrab.domain.nutrition

import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.MealPreset

fun NutritionEstimate.hasValidNutrition(): Boolean =
    itemName.isNotBlank() &&
        kcal.isFinite() && kcal > 0.0 &&
        grams.isFinite() && grams >= 0.0 &&
        proteinG.isFinite() && proteinG >= 0.0 &&
        carbsG.isFinite() && carbsG >= 0.0 &&
        fatG.isFinite() && fatG >= 0.0 &&
        fiberG.isFinite() && fiberG >= 0.0

fun FoodEntry.hasValidNutrition(): Boolean =
    itemName.isNotBlank() &&
        kcal.isFinite() && kcal > 0.0 &&
        grams.isFinite() && grams >= 0.0 &&
        proteinG.isFinite() && proteinG >= 0.0 &&
        carbsG.isFinite() && carbsG >= 0.0 &&
        fatG.isFinite() && fatG >= 0.0 &&
        fiberG.isFinite() && fiberG >= 0.0

fun MealPreset.hasValidNutrition(): Boolean =
    name.isNotBlank() &&
        kcal.isFinite() && kcal > 0.0 &&
        grams.isFinite() && grams >= 0.0 &&
        proteinG.isFinite() && proteinG >= 0.0 &&
        carbsG.isFinite() && carbsG >= 0.0 &&
        fatG.isFinite() && fatG >= 0.0 &&
        fiberG.isFinite() && fiberG >= 0.0

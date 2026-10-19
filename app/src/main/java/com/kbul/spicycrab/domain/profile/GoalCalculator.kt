package com.kbul.spicycrab.domain.profile

import com.kbul.spicycrab.data.prefs.BodyProfile
import com.kbul.spicycrab.data.prefs.NutritionGoals
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Mifflin–St Jeor offsets; "prefer not to say" takes the midpoint of the two. */
enum class Sex(val bmrOffset: Int, val kcalFloor: Int) {
    MALE(5, 1500),
    FEMALE(-161, 1200),
    UNSPECIFIED(-78, 1350);

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: UNSPECIFIED
    }
}

enum class ActivityLevel(val factor: Double) {
    SEDENTARY(1.2),
    LIGHT(1.375),
    MODERATE(1.55),
    ACTIVE(1.725),
    VERY_ACTIVE(1.9);

    companion object {
        fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: LIGHT
    }
}

enum class GoalDirection { LOSE, MAINTAIN, GAIN }

enum class GoalWarning {
    /** Under 18: maintenance only, never a deficit. */
    MINOR,
    /** The deficit would go below the minimum intake, so it was clamped. */
    FLOOR_APPLIED,
    /** Goal weight is below a BMI of 18.5 for the given height. */
    GOAL_BELOW_HEALTHY,
}

data class GoalSuggestion(
    val goals: NutritionGoals,
    val maintenanceKcal: Int,
    val direction: GoalDirection,
    val weeksToGoal: Int?,
    val warnings: Set<GoalWarning>,
)

enum class BmiCategory { UNDERWEIGHT, HEALTHY, OVERWEIGHT, OBESE }

object GoalCalculator {
    const val PACE_SLOW = 0.25
    const val PACE_MEDIUM = 0.5
    const val PACE_FAST = 0.75
    val PACES = listOf(PACE_SLOW, PACE_MEDIUM, PACE_FAST)

    // Stand-ins for skipped fields. BMI never uses the height default — it is shown only for a real height.
    private const val DEFAULT_HEIGHT_CM = 170
    private const val DEFAULT_AGE = 30
    private const val KCAL_PER_KG = 7700.0
    private const val HEALTHY_BMI_MIN = 18.5
    private const val HEALTHY_BMI_MAX = 24.9

    fun suggest(weightKg: Double, profile: BodyProfile, currentYear: Int): GoalSuggestion {
        val sex = Sex.fromName(profile.sex)
        val heightCm = profile.heightCm ?: DEFAULT_HEIGHT_CM
        val age = profile.birthYear?.let { currentYear - it } ?: DEFAULT_AGE
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * age + sex.bmrOffset
        val maintenance = bmr * ActivityLevel.fromName(profile.activity).factor

        val goal = profile.goalWeightKg
        val warnings = mutableSetOf<GoalWarning>()
        var direction = when {
            goal == null || kotlin.math.abs(goal - weightKg) < 0.5 -> GoalDirection.MAINTAIN
            goal < weightKg -> GoalDirection.LOSE
            else -> GoalDirection.GAIN
        }
        if (direction == GoalDirection.LOSE && age < 18) {
            direction = GoalDirection.MAINTAIN
            warnings += GoalWarning.MINOR
        }
        if (goal != null && profile.heightCm != null && bmi(goal, profile.heightCm) < HEALTHY_BMI_MIN) {
            warnings += GoalWarning.GOAL_BELOW_HEALTHY
        }

        val dailyDelta = profile.paceKgPerWeek * KCAL_PER_KG / 7
        val kcal = when (direction) {
            GoalDirection.MAINTAIN -> maintenance
            GoalDirection.GAIN -> maintenance + dailyDelta
            GoalDirection.LOSE -> {
                // Never below the floor — unless maintenance itself is, in which case no deficit at all.
                val floor = minOf(sex.kcalFloor.toDouble(), maintenance)
                if (maintenance - dailyDelta < floor) warnings += GoalWarning.FLOOR_APPLIED
                maxOf(maintenance - dailyDelta, floor)
            }
        }
        val roundedKcal = (kcal / 10).roundToInt() * 10

        val weeks = if (direction == GoalDirection.MAINTAIN || goal == null) null
        else ceil(kotlin.math.abs(goal - weightKg) / profile.paceKgPerWeek).toInt()

        return GoalSuggestion(
            goals = macrosFor(roundedKcal, referenceWeightKg = goal ?: weightKg),
            maintenanceKcal = (maintenance / 10).roundToInt() * 10,
            direction = direction,
            weeksToGoal = weeks,
            warnings = warnings,
        )
    }

    /** 1.6 g/kg protein (of the goal weight, so it doesn't balloon with body fat), 30% fat, carbs fill the rest. */
    private fun macrosFor(kcal: Int, referenceWeightKg: Double): NutritionGoals {
        val protein = (1.6 * referenceWeightKg).roundToInt()
        val fat = (kcal * 0.30 / 9).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        val fiber = (kcal * 14 / 1000.0).roundToInt()
        return NutritionGoals(kcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat, fiberG = fiber)
    }

    fun bmi(weightKg: Double, heightCm: Int): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun bmiCategory(bmi: Double): BmiCategory = when {
        bmi < HEALTHY_BMI_MIN -> BmiCategory.UNDERWEIGHT
        bmi < 25.0 -> BmiCategory.HEALTHY
        bmi < 30.0 -> BmiCategory.OVERWEIGHT
        else -> BmiCategory.OBESE
    }

    fun healthyWeightRangeKg(heightCm: Int): ClosedFloatingPointRange<Double> {
        val m2 = (heightCm / 100.0).let { it * it }
        return (HEALTHY_BMI_MIN * m2)..(HEALTHY_BMI_MAX * m2)
    }
}

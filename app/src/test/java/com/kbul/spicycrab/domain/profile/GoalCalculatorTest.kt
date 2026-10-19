package com.kbul.spicycrab.domain.profile

import com.kbul.spicycrab.data.prefs.BodyProfile
import com.kbul.spicycrab.data.prefs.NutritionGoals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCalculatorTest {

    @Test
    fun deficitFromMifflinStJeor() {
        val profile = BodyProfile(heightCm = 180, sex = "MALE", birthYear = 1996, activity = "MODERATE", goalWeightKg = 80.0, paceKgPerWeek = 0.5)
        val s = GoalCalculator.suggest(90.0, profile, currentYear = 2026)
        // BMR 1880 × 1.55 = 2914 maintenance; −550/day for 0.5 kg/week.
        assertEquals(2910, s.maintenanceKcal)
        assertEquals(NutritionGoals(kcal = 2360, proteinG = 128, carbsG = 284, fatG = 79, fiberG = 33), s.goals)
        assertEquals(GoalDirection.LOSE, s.direction)
        assertEquals(20, s.weeksToGoal)
        assertTrue(s.warnings.isEmpty())
    }

    @Test
    fun deficitNeverGoesBelowTheFloor() {
        val profile = BodyProfile(heightCm = 155, sex = "FEMALE", birthYear = 1976, activity = "SEDENTARY", goalWeightKg = 50.0, paceKgPerWeek = 0.75)
        val s = GoalCalculator.suggest(60.0, profile, currentYear = 2026)
        assertEquals(1200, s.goals.kcal)
        assertEquals(setOf(GoalWarning.FLOOR_APPLIED), s.warnings)
    }

    @Test
    fun minorsGetMaintenanceInsteadOfADeficit() {
        val profile = BodyProfile(heightCm = 165, birthYear = 2012, goalWeightKg = 55.0)
        val s = GoalCalculator.suggest(60.0, profile, currentYear = 2026)
        assertEquals(GoalDirection.MAINTAIN, s.direction)
        assertEquals(s.maintenanceKcal, s.goals.kcal)
        assertNull(s.weeksToGoal)
        assertTrue(GoalWarning.MINOR in s.warnings)
    }

    @Test
    fun goalBelowHealthyBmiIsFlagged() {
        val profile = BodyProfile(heightCm = 170, goalWeightKg = 50.0)
        val s = GoalCalculator.suggest(65.0, profile, currentYear = 2026)
        assertTrue(GoalWarning.GOAL_BELOW_HEALTHY in s.warnings)
    }

    @Test
    fun skippedFieldsFallBackToDefaultsAndMaintenance() {
        // 170 cm, age 30, midpoint sex, light activity: (700 + 1062.5 − 150 − 78) × 1.375 = 2110.
        val s = GoalCalculator.suggest(70.0, BodyProfile(), currentYear = 2026)
        assertEquals(GoalDirection.MAINTAIN, s.direction)
        assertEquals(2110, s.goals.kcal)
        assertTrue(s.warnings.isEmpty())
    }

    @Test
    fun bmiAndHealthyRange() {
        assertEquals(24.69, GoalCalculator.bmi(80.0, 180), 0.01)
        assertEquals(BmiCategory.HEALTHY, GoalCalculator.bmiCategory(24.69))
        assertEquals(BmiCategory.OVERWEIGHT, GoalCalculator.bmiCategory(25.0))
        val range = GoalCalculator.healthyWeightRangeKg(180)
        assertEquals(59.9, range.start, 0.1)
        assertEquals(80.7, range.endInclusive, 0.1)
    }
}

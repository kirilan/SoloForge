package com.kbul.spicycrab.domain.nutrition

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NeedsEscalationTest {

    @Test
    fun lowConfidenceEscalatesRegardlessOfText() {
        assertTrue(estimate(confidence = "low").needsEscalation())
        assertTrue(estimate(confidence = "LOW").needsEscalation())
    }

    @Test
    fun confidentSimpleFoodDoesNotEscalate() {
        assertFalse(estimate(itemName = "Banana", notes = "a ripe banana").needsEscalation())
    }

    @Test
    fun mixedMealKeywordsEscalate() {
        assertTrue(estimate(itemName = "Mixed meal plate").needsEscalation())
        assertTrue(estimate(notes = "possible hidden ingredients").needsEscalation())
        assertTrue(estimate(notes = "salad with dressing").needsEscalation())
        assertTrue(estimate(notes = "cooked in oil").needsEscalation())
        assertTrue(estimate(notes = "portion size unclear").needsEscalation())
    }

    @Test
    fun keywordMatchIsCaseInsensitiveViaLowercasing() {
        assertTrue(estimate(itemName = "ASSORTED sushi").needsEscalation())
    }

    private fun estimate(
        itemName: String = "Plain rice",
        confidence: String = "high",
        notes: String = "",
    ) = NutritionEstimate(
        itemName = itemName,
        grams = 100.0,
        kcal = 130.0,
        proteinG = 2.5,
        carbsG = 28.0,
        fatG = 0.3,
        fiberG = 0.4,
        confidence = confidence,
        notes = notes,
    )
}

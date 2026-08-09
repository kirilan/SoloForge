package com.kbul.spicycrab.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisPolicyTest {

    @Test
    fun cleanEstimateIsAccepted() {
        assertEquals(AnalysisAction.ACCEPT, estimate().routedAction(hasImage = true))
    }

    @Test
    fun proseCanNoLongerRoute() {
        val chatty = estimate(
            itemName = "Mixed meal with hidden sauces",
            notes = "portion unclear, cooked in oil, assorted items",
        )
        assertEquals(AnalysisAction.ACCEPT, chatty.routedAction(hasImage = true))
    }

    @Test
    fun everyReasonRoutesToItsIntendedAction() {
        assertEquals(
            AnalysisAction.RETRY_IMAGE,
            estimate(reasons = listOf(UncertaintyReason.IMAGE_QUALITY)).routedAction(hasImage = true),
        )
        listOf(
            UncertaintyReason.IDENTITY_AMBIGUOUS,
            UncertaintyReason.PORTION_UNKNOWN,
            UncertaintyReason.PREPARATION_UNKNOWN,
            UncertaintyReason.HIDDEN_INGREDIENTS,
        ).forEach {
            assertEquals(AnalysisAction.ASK_USER, estimate(reasons = listOf(it)).routedAction(hasImage = true))
        }
    }

    @Test
    fun retryImageWithoutAnImageBecomesAskUser() {
        val est = estimate(reasons = listOf(UncertaintyReason.IMAGE_QUALITY))
        assertEquals(AnalysisAction.ASK_USER, est.routedAction(hasImage = false))
    }

    @Test
    fun lowConfidenceAsksUserEvenWithoutReasons() {
        assertEquals(AnalysisAction.ASK_USER, estimate(confidence = "LOW").routedAction(hasImage = true))
    }

    @Test
    fun modelActionIsHonouredWhenNothingElseTriggers() {
        val est = estimate(reported = AnalysisAction.ASK_USER)
        assertEquals(AnalysisAction.ASK_USER, est.routedAction(hasImage = true))
    }

    @Test
    fun strongerModelOnlyAdvertisedWhenItCanHelp() {
        assertTrue(estimate(reasons = listOf(UncertaintyReason.IDENTITY_AMBIGUOUS)).strongerModelCanHelp())
        assertFalse(estimate(reasons = listOf(UncertaintyReason.PORTION_UNKNOWN)).strongerModelCanHelp())
        assertFalse(estimate(reasons = listOf(UncertaintyReason.HIDDEN_INGREDIENTS)).strongerModelCanHelp())
    }

    @Test
    fun malformedNumbersAreIncoherent() {
        assertFalse(estimate(kcal = Double.NaN).isCoherent())
        assertFalse(estimate(kcal = -10.0).isCoherent())
        assertFalse(estimate(items = listOf(item(kcal = Double.POSITIVE_INFINITY))).isCoherent())
        assertFalse(estimate(items = listOf(item(grams = -5.0))).isCoherent())
    }

    @Test
    fun caloriesGrosslyInconsistentWithMacrosAskUser() {
        val bogus = estimate(kcal = 2000.0, proteinG = 5.0, carbsG = 10.0, fatG = 2.0)
        assertFalse(bogus.isCoherent())
        assertEquals(AnalysisAction.ASK_USER, bogus.routedAction(hasImage = true))
    }

    @Test
    fun itemsThatDoNotSumToTheMealAskUser() {
        val split = estimate(
            kcal = 900.0,
            proteinG = 45.0,
            carbsG = 90.0,
            fatG = 40.0,
            items = listOf(item(kcal = 100.0), item(kcal = 80.0)),
        )
        assertFalse(split.isCoherent())
        assertEquals(AnalysisAction.ASK_USER, split.routedAction(hasImage = true))
    }

    @Test
    fun itemsThatRoughlySumAreFine() {
        val plate = estimate(
            kcal = 520.0,
            proteinG = 30.0,
            carbsG = 60.0,
            fatG = 18.0,
            items = listOf(item(kcal = 300.0), item(kcal = 240.0)),
        )
        assertTrue(plate.isCoherent())
    }

    @Test
    fun unknownEnumStringsDegradeSafely() {
        assertEquals(null, UncertaintyReason.parse("banana"))
        assertEquals(UncertaintyReason.PORTION_UNKNOWN, UncertaintyReason.parse(" Portion_Unknown "))
        assertEquals(AnalysisAction.ASK_USER, AnalysisAction.parse("shrug"))
        assertEquals(AnalysisAction.ACCEPT, AnalysisAction.parse("accept"))
    }

    private fun item(
        kcal: Double = 100.0,
        grams: Double = 50.0,
    ) = EstimateItem(
        name = "component",
        grams = grams,
        kcal = kcal,
        proteinG = 5.0,
        carbsG = 10.0,
        fatG = 3.0,
        fiberG = 1.0,
    )

    private fun estimate(
        itemName: String = "Plain rice",
        kcal: Double = 130.0,
        proteinG: Double = 2.5,
        carbsG: Double = 28.0,
        fatG: Double = 0.3,
        confidence: String = "high",
        notes: String = "",
        items: List<EstimateItem> = emptyList(),
        reasons: List<UncertaintyReason> = emptyList(),
        reported: AnalysisAction = AnalysisAction.ACCEPT,
    ) = NutritionEstimate(
        itemName = itemName,
        grams = 100.0,
        kcal = kcal,
        proteinG = proteinG,
        carbsG = carbsG,
        fatG = fatG,
        fiberG = 0.4,
        confidence = confidence,
        notes = notes,
        items = items,
        uncertaintyReasons = reasons,
        reportedAction = reported,
    )
}

package com.kbul.spicycrab.domain.nutrition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisConfigTest {

    @Test
    fun defaultIsTheFastRowSoDoingNothingKeepsExistingBehaviour() {
        assertEquals(FoodAnalysisModels.TOKEN_FAST, FoodAnalysisModels.DEFAULT.token)
        assertEquals("google/gemini-3.1-flash-lite", FoodAnalysisModels.DEFAULT.modelId)
    }

    /** SettingsRepo mirrors this literal to avoid a data-to-domain import; keep them in step. */
    @Test
    fun fastTokenLiteralMatchesTheOneSettingsRepoStores() {
        assertEquals("fast", FoodAnalysisModels.TOKEN_FAST)
    }

    @Test
    fun everyOfferedTokenResolvesToItself() {
        FoodAnalysisModels.OFFERED.forEach { config ->
            assertEquals(config, FoodAnalysisModels.resolve(config.token, ""))
        }
    }

    @Test
    fun offeredTokensAreUniqueAndNoneCollideWithCustom() {
        val tokens = FoodAnalysisModels.OFFERED.map { it.token }
        assertEquals(tokens.size, tokens.toSet().size)
        assertFalse(tokens.contains(FoodAnalysisModels.TOKEN_CUSTOM))
    }

    @Test
    fun unknownTokenFallsBackToDefault() {
        assertEquals(FoodAnalysisModels.DEFAULT, FoodAnalysisModels.resolve("nonsense", ""))
        assertEquals(FoodAnalysisModels.DEFAULT, FoodAnalysisModels.resolve("", ""))
    }

    @Test
    fun customTokenUsesTheSuppliedId() {
        val config = FoodAnalysisModels.resolve(FoodAnalysisModels.TOKEN_CUSTOM, "vendor/some-model")
        assertEquals("vendor/some-model", config.modelId)
        assertEquals(FoodAnalysisModels.TOKEN_CUSTOM, config.token)
    }

    @Test
    fun customTokenTrimsWhitespaceAroundTheId() {
        val config = FoodAnalysisModels.resolve(FoodAnalysisModels.TOKEN_CUSTOM, "  vendor/m  ")
        assertEquals("vendor/m", config.modelId)
    }

    @Test
    fun blankCustomIdFallsBackToDefaultRatherThanRequestingAnEmptyModel() {
        assertEquals(FoodAnalysisModels.DEFAULT, FoodAnalysisModels.resolve(FoodAnalysisModels.TOKEN_CUSTOM, ""))
        assertEquals(FoodAnalysisModels.DEFAULT, FoodAnalysisModels.resolve(FoodAnalysisModels.TOKEN_CUSTOM, "   "))
    }

    /** A user-supplied model has no eval behind it, so the UI must have nothing to print. */
    @Test
    fun customConfigIsNotMeasuredAndHasNoEscalation() {
        val config = FoodAnalysisModels.custom("vendor/unknown")
        assertFalse(config.isMeasured)
        assertNull(config.escalationId)
        assertEquals(0, config.casesScored)
    }

    @Test
    fun everyOfferedRowIsMeasured() {
        FoodAnalysisModels.OFFERED.forEach {
            assertTrue("${it.token} must carry eval numbers", it.isMeasured)
            assertTrue(it.casesScored > 0)
            assertTrue(it.answersDirectly in 0..it.casesScored)
            assertTrue(it.kcalErrorPercent > 0.0)
            assertTrue(it.medianPhotoSeconds > 0.0)
            assertTrue(it.centsPerThousandAnalyses > 0)
        }
    }

    /**
     * The eval found nothing above the accurate row and no open-weight model above the open row,
     * so both must hide the retry button rather than escalate sideways or out of licence.
     */
    @Test
    fun rowsWithoutAStrongerModelDeclareNoEscalation() {
        assertNull(FoodAnalysisModels.ACCURATE.escalationId)
        assertNull(FoodAnalysisModels.OPEN.escalationId)
    }

    @Test
    fun geminiRowsEscalateToTheMeasuredStrongerModel() {
        assertEquals("google/gemini-3.1-pro-preview", FoodAnalysisModels.FAST.escalationId)
        assertEquals("google/gemini-3.1-pro-preview", FoodAnalysisModels.BALANCED.escalationId)
    }

    @Test
    fun noRowEscalatesToItself() {
        FoodAnalysisModels.OFFERED.forEach {
            assertTrue("${it.token} escalates to itself", it.escalationId != it.modelId)
        }
    }

    /** Escalating an open-weight row to a proprietary model would end the row's only promise. */
    @Test
    fun openWeightRowNeverEscalatesOutOfItsLicence() {
        val open = FoodAnalysisModels.OFFERED.filter { it.openWeight }
        assertTrue(open.isNotEmpty())
        open.forEach { assertNull(it.escalationId) }
    }

    @Test
    fun everyEscalationTargetIsAModelWeHaveMeasured() {
        val measured = FoodAnalysisModels.OFFERED.map { it.modelId }.toSet()
        FoodAnalysisModels.OFFERED.mapNotNull { it.escalationId }.forEach {
            assertTrue("$it is offered as an escalation but never evaluated", measured.contains(it))
        }
    }

    @Test
    fun accurateRowIsTheMostAccurateOffered() {
        val best = FoodAnalysisModels.OFFERED.minByOrNull { it.kcalErrorPercent }
        assertNotNull(best)
        assertEquals(FoodAnalysisModels.ACCURATE, best)
    }

    @Test
    fun fastRowIsTheFastestOffered() {
        assertEquals(
            FoodAnalysisModels.FAST,
            FoodAnalysisModels.OFFERED.minByOrNull { it.medianPhotoSeconds },
        )
    }
}

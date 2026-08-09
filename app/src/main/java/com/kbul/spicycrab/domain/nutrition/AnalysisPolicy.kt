package com.kbul.spicycrab.domain.nutrition

import kotlin.math.abs

/**
 * Routing is decided here from enumerated fields only — never from model prose.
 * Android- and network-free so it stays unit-testable.
 */
fun NutritionEstimate.routedAction(hasImage: Boolean): AnalysisAction {
    val action = when {
        !isCoherent() -> AnalysisAction.ASK_USER
        UncertaintyReason.IMAGE_QUALITY in uncertaintyReasons -> AnalysisAction.RETRY_IMAGE
        uncertaintyReasons.isNotEmpty() -> AnalysisAction.ASK_USER
        confidence.equals("low", ignoreCase = true) -> AnalysisAction.ASK_USER
        else -> reportedAction
    }
    return if (action == AnalysisAction.RETRY_IMAGE && !hasImage) AnalysisAction.ASK_USER else action
}

/** A stronger model can only help when the food itself was hard to recognize. */
fun NutritionEstimate.strongerModelCanHelp(): Boolean =
    uncertaintyReasons.isEmpty() || UncertaintyReason.IDENTITY_AMBIGUOUS in uncertaintyReasons

/** Numbers are finite and non-negative, components reconcile, and calories match the macros. */
fun NutritionEstimate.isCoherent(): Boolean =
    hasValidNutrition() && items.all { it.isSane() } && itemsReconcile() && caloriesMatchMacros()

private fun EstimateItem.isSane(): Boolean =
    listOf(grams, kcal, proteinG, carbsG, fatG, fiberG).all { it.isFinite() && it >= 0.0 }

private fun NutritionEstimate.itemsReconcile(): Boolean {
    if (items.isEmpty()) return true
    return closeEnough(items.sumOf { it.kcal }, kcal)
}

private fun NutritionEstimate.caloriesMatchMacros(): Boolean =
    closeEnough(4 * proteinG + 4 * carbsG + 9 * fatG, kcal)

// ponytail: one flat tolerance for both checks; split it only if a real case needs different slack.
private fun closeEnough(a: Double, b: Double): Boolean {
    val diff = abs(a - b)
    return diff <= 100.0 || diff <= 0.4 * maxOf(a, b)
}

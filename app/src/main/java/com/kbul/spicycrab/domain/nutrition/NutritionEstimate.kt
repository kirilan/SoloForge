package com.kbul.spicycrab.domain.nutrition

enum class UncertaintyReason {
    IMAGE_QUALITY,
    IDENTITY_AMBIGUOUS,
    PORTION_UNKNOWN,
    PREPARATION_UNKNOWN,
    HIDDEN_INGREDIENTS;

    companion object {
        fun parse(raw: String): UncertaintyReason? =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) }
    }
}

enum class AnalysisAction {
    ACCEPT,
    ASK_USER,
    RETRY_IMAGE;

    companion object {
        fun parse(raw: String): AnalysisAction =
            entries.firstOrNull { it.name.equals(raw.trim(), ignoreCase = true) } ?: ASK_USER
    }
}

data class EstimateItem(
    val name: String,
    val grams: Double,
    val kcal: Double,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val fiberG: Double,
)

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
    val modelUsed: String = "",
    val items: List<EstimateItem> = emptyList(),
    val uncertaintyReasons: List<UncertaintyReason> = emptyList(),
    val reportedAction: AnalysisAction = AnalysisAction.ACCEPT,
)

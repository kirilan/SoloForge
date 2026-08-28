package com.kbul.spicycrab.domain.nutrition

import android.content.Context
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.dao.MealPresetDao
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.MealPreset
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.network.OpenRouterClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.CancellationException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One selectable analysis configuration: the model that answers, and the model the user's
 * explicit "retry with a stronger model" tap escalates to.
 *
 * Escalation belongs to the row rather than being one global constant. The 2026-08-28 eval found
 * nothing that beats [FoodAnalysisModels.ACCURATE]'s model and no Apache-2.0 model that beats
 * [FoodAnalysisModels.OPEN]'s, so both carry a null [escalationId] and hide the retry button
 * rather than offering a sideways move dressed up as an upgrade.
 *
 * The measured fields come from 44-case runs at the shipped prompt and schema. `tools/food_eval`
 * re-runs every row and every escalation before a release touches a model id, the prompt,
 * temperature, image preprocessing, or the schema — see docs/curated-model-choice-plan.md.
 */
data class AnalysisConfig(
    val token: String,
    val modelId: String,
    val escalationId: String?,
    val openWeight: Boolean,
    /** Mean absolute calorie error in percent, over the eval cases carrying ground truth. */
    val kcalErrorPercent: Double,
    /** Cases answered outright, without a follow-up question, out of [casesScored]. */
    val answersDirectly: Int,
    val casesScored: Int,
    val medianPhotoSeconds: Double,
    /**
     * US cents per 1000 analyses. Token counts were measured on [FoodAnalysisModels.FAST]
     * (1662 prompt + 261 completion) and applied to each model's published rate; per-model token
     * counts still need the release re-run. The rows differ by more than 20x, so the figure is
     * sound at the precision the UI shows it.
     */
    val centsPerThousandAnalyses: Int,
) {
    /** A user-supplied id carries no measurements, so the UI must not print any. */
    val isMeasured: Boolean get() = casesScored > 0
}

object FoodAnalysisModels {
    const val TOKEN_FAST = "fast"
    const val TOKEN_BALANCED = "balanced"
    const val TOKEN_OPEN = "open"
    const val TOKEN_ACCURATE = "accurate"
    const val TOKEN_CUSTOM = "custom"

    val FAST = AnalysisConfig(
        token = TOKEN_FAST,
        modelId = "google/gemini-3.1-flash-lite",
        escalationId = "google/gemini-3.1-pro-preview",
        openWeight = false,
        kcalErrorPercent = 23.3,
        answersDirectly = 16,
        casesScored = 44,
        medianPhotoSeconds = 2.2,
        centsPerThousandAnalyses = 81,
    )

    val BALANCED = AnalysisConfig(
        token = TOKEN_BALANCED,
        modelId = "google/gemini-3.7-flash",
        escalationId = "google/gemini-3.1-pro-preview",
        openWeight = false,
        kcalErrorPercent = 21.3,
        answersDirectly = 23,
        casesScored = 44,
        medianPhotoSeconds = 7.0,
        centsPerThousandAnalyses = 223,
    )

    // No open-weight model measured beats this one, so escalating would leave Apache-2.0
    // for a proprietary model and end the row's only promise at the first tap.
    val OPEN = AnalysisConfig(
        token = TOKEN_OPEN,
        modelId = "qwen/qwen3-vl-32b-instruct",
        escalationId = null,
        openWeight = true,
        kcalErrorPercent = 26.2,
        answersDirectly = 14,
        casesScored = 44,
        medianPhotoSeconds = 6.9,
        centsPerThousandAnalyses = 28,
    )

    // The escalation target itself; there is nothing measured above it to escalate to.
    val ACCURATE = AnalysisConfig(
        token = TOKEN_ACCURATE,
        modelId = "google/gemini-3.1-pro-preview",
        escalationId = null,
        openWeight = false,
        kcalErrorPercent = 17.4,
        answersDirectly = 15,
        casesScored = 44,
        medianPhotoSeconds = 5.4,
        centsPerThousandAnalyses = 646,
    )

    val OFFERED = listOf(FAST, BALANCED, OPEN, ACCURATE)

    val DEFAULT = FAST

    /** A user-supplied id: never evaluated, so no measured numbers and no escalation. */
    fun custom(modelId: String) = AnalysisConfig(
        token = TOKEN_CUSTOM,
        modelId = modelId,
        escalationId = null,
        openWeight = false,
        kcalErrorPercent = 0.0,
        answersDirectly = 0,
        casesScored = 0,
        medianPhotoSeconds = 0.0,
        centsPerThousandAnalyses = 0,
    )

    /** Prefs boundary: an unknown token or a blank custom id falls back to the default. */
    fun resolve(token: String, customModelId: String): AnalysisConfig {
        if (token == TOKEN_CUSTOM) {
            val id = customModelId.trim()
            return if (id.isEmpty()) DEFAULT else custom(id)
        }
        return OFFERED.firstOrNull { it.token == token } ?: DEFAULT
    }
}

@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodEntryDao,
    private val presetDao: MealPresetDao,
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val settings: SettingsRepo,
    @param:ApplicationContext private val context: Context,
) {

    fun observeAll(): Flow<List<FoodEntry>> = dao.observeAll()

    suspend fun analyze(
        imageFile: File,
        comment: String,
        model: String = FoodAnalysisModels.DEFAULT.modelId,
    ): Result<NutritionEstimate> {
        val base64 = runCatching { ImageUtils.fileToBase64Jpeg(imageFile) }
            .getOrElse { return Result.failure(it) }
        return analyzeOnce(base64, comment, model)
    }

    suspend fun analyzeText(
        description: String,
        model: String = FoodAnalysisModels.DEFAULT.modelId,
    ): Result<NutritionEstimate> = analyzeOnce(null, description, model)

    private suspend fun analyzeOnce(
        base64: String?,
        comment: String,
        model: String,
    ): Result<NutritionEstimate> {
        if (!settings.current().aiFeaturesEnabled) {
            return Result.failure(IllegalStateException(context.getString(R.string.error_ai_disabled)))
        }
        val key = keyStore.getOpenRouterKey()
            ?: return Result.failure(IllegalStateException(context.getString(R.string.error_no_api_key)))

        val result = runCatching { analyzeWithModel(key, model, base64, comment) }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        return result
    }

    suspend fun save(
        estimate: NutritionEstimate,
        comment: String,
        imageFile: File?,
    ): FoodEntry {
        require(estimate.hasValidNutrition()) { context.getString(R.string.error_invalid_nutrition) }
        val s = settings.current()
        val savedImagePath = if (s.savePhotoLocally && imageFile != null) {
            val dest = File(context.filesDir, "food_${System.currentTimeMillis()}.jpg")
            imageFile.copyTo(dest, overwrite = true)
            dest.absolutePath
        } else null

        val now = System.currentTimeMillis()
        val entry = FoodEntry(
            timestampEpoch = now,
            lastModifiedEpoch = now,
            itemName = estimate.itemName,
            grams = estimate.grams,
            kcal = estimate.kcal,
            proteinG = estimate.proteinG,
            carbsG = estimate.carbsG,
            fatG = estimate.fatG,
            fiberG = estimate.fiberG,
            comment = comment,
            modelUsed = estimate.modelUsed.ifBlank { FoodAnalysisModels.DEFAULT.modelId },
            confidence = estimate.confidence,
            imagePath = savedImagePath,
        )
        return runCatching { entry.copy(id = dao.insert(entry)) }
            .onFailure { FoodPhotoFiles.deleteOwned(context, savedImagePath) }
            .getOrThrow()
    }

    suspend fun addManual(draft: FoodEntry): FoodEntry {
        require(draft.hasValidNutrition()) { context.getString(R.string.error_invalid_nutrition) }
        val now = System.currentTimeMillis()
        return insertEntry(
            draft.copy(
                timestampEpoch = draft.timestampEpoch.takeIf { it > 0 } ?: now,
                lastModifiedEpoch = now,
                modelUsed = "manual",
                confidence = "user",
                imagePath = null,
            )
        )
    }

    fun observePresets(): Flow<List<MealPreset>> = presetDao.observeAll()

    suspend fun saveAsPreset(source: FoodEntry, name: String): MealPreset {
        require(source.hasValidNutrition()) { context.getString(R.string.error_invalid_nutrition) }
        val preset = MealPreset(
            name = name.ifBlank { source.itemName }.trim(),
            grams = source.grams,
            kcal = source.kcal,
            proteinG = source.proteinG,
            carbsG = source.carbsG,
            fatG = source.fatG,
            fiberG = source.fiberG,
            comment = source.comment,
            createdEpoch = System.currentTimeMillis(),
        )
        return preset.copy(id = presetDao.insert(preset))
    }

    suspend fun deletePreset(preset: MealPreset) = presetDao.delete(preset)

    suspend fun logPreset(preset: MealPreset): FoodEntry {
        require(preset.hasValidNutrition()) { context.getString(R.string.error_invalid_nutrition) }
        val now = System.currentTimeMillis()
        return insertEntry(
            FoodEntry(
                timestampEpoch = now,
                lastModifiedEpoch = now,
                itemName = preset.name,
                grams = preset.grams,
                kcal = preset.kcal,
                proteinG = preset.proteinG,
                carbsG = preset.carbsG,
                fatG = preset.fatG,
                fiberG = preset.fiberG,
                comment = preset.comment,
                modelUsed = "preset",
                confidence = "user",
                imagePath = null,
            )
        )
    }

    private suspend fun insertEntry(entry: FoodEntry): FoodEntry =
        entry.copy(id = dao.insert(entry))

    suspend fun update(updated: FoodEntry): FoodEntry {
        require(updated.hasValidNutrition()) { context.getString(R.string.error_invalid_nutrition) }
        val bumped = updated.copy(lastModifiedEpoch = System.currentTimeMillis())
        dao.update(bumped)
        return bumped
    }

    suspend fun delete(entry: FoodEntry) {
        dao.delete(entry)
        FoodPhotoFiles.deleteOwned(context, entry.imagePath)
    }

    fun todayTotals(entries: List<FoodEntry>): NutritionEstimate = NutritionEstimate(
        itemName = "today",
        grams = entries.sumOf { it.grams },
        kcal = entries.sumOf { it.kcal },
        proteinG = entries.sumOf { it.proteinG },
        carbsG = entries.sumOf { it.carbsG },
        fatG = entries.sumOf { it.fatG },
        fiberG = entries.sumOf { it.fiberG },
        confidence = "",
        notes = "",
    )

    fun mostRecentEpoch(entries: List<FoodEntry>): Long? = entries.firstOrNull()?.timestampEpoch

    private suspend fun analyzeWithModel(
        key: String,
        model: String,
        base64: String?,
        comment: String,
    ): NutritionEstimate =
        if (!settings.current().aiFeaturesEnabled) {
            throw IllegalStateException(context.getString(R.string.error_ai_disabled))
        } else client.analyzeFood(key, model, base64, comment).getOrThrow().let { dto ->
            NutritionEstimate(
                itemName = dto.itemName,
                grams = dto.estimatedGrams,
                kcal = dto.calories,
                proteinG = dto.proteinG,
                carbsG = dto.carbsG,
                fatG = dto.fatG,
                fiberG = dto.fiberG,
                confidence = dto.confidence,
                notes = dto.notes,
                modelUsed = model,
                items = dto.items.map {
                    EstimateItem(
                        name = it.name,
                        grams = it.estimatedGrams,
                        kcal = it.calories,
                        proteinG = it.proteinG,
                        carbsG = it.carbsG,
                        fatG = it.fatG,
                        fiberG = it.fiberG,
                    )
                },
                uncertaintyReasons = dto.uncertaintyReasons.mapNotNull(UncertaintyReason::parse),
                reportedAction = AnalysisAction.parse(dto.recommendedAction),
            )
        }
}

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

object FoodAnalysisModels {
    const val DEFAULT = "google/gemini-3.1-flash-lite"

    /** Never called automatically — only when the user taps retry. */
    const val ON_DEMAND_RETRY = "google/gemini-3.1-pro-preview"
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
        model: String = FoodAnalysisModels.DEFAULT,
    ): Result<NutritionEstimate> {
        val base64 = runCatching { ImageUtils.fileToBase64Jpeg(imageFile) }
            .getOrElse { return Result.failure(it) }
        return analyzeOnce(base64, comment, model)
    }

    suspend fun analyzeText(
        description: String,
        model: String = FoodAnalysisModels.DEFAULT,
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
            modelUsed = estimate.modelUsed.ifBlank { FoodAnalysisModels.DEFAULT },
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

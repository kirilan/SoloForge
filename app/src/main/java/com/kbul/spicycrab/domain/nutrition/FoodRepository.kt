package com.kbul.spicycrab.domain.nutrition

import android.content.Context
import android.net.Uri
import com.kbul.spicycrab.data.csv.CsvExporter
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.network.OpenRouterClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FoodRepository @Inject constructor(
    private val dao: FoodEntryDao,
    private val client: OpenRouterClient,
    private val keyStore: SecureKeyStore,
    private val settings: SettingsRepo,
    private val csvExporter: CsvExporter,
    @ApplicationContext private val context: Context,
) {

    fun observeAll(): Flow<List<FoodEntry>> = dao.observeAll()

    fun observeToday(zone: ZoneId = ZoneId.systemDefault()): Flow<List<FoodEntry>> {
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return dao.observeForDay(start, end)
    }

    suspend fun analyze(imageFile: File, comment: String): Result<NutritionEstimate> {
        val key = keyStore.getOpenRouterKey()
            ?: return Result.failure(IllegalStateException("Set your OpenRouter API key in Settings."))
        val model = settings.current().selectedModel
        val base64 = runCatching { ImageUtils.fileToBase64Jpeg(imageFile) }
            .getOrElse { return Result.failure(it) }

        return client.analyzeFood(key, model, base64, comment).map { dto ->
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
            )
        }
    }

    suspend fun save(
        estimate: NutritionEstimate,
        comment: String,
        imageFile: File?,
    ): FoodEntry {
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
            modelUsed = s.selectedModel,
            confidence = estimate.confidence,
            imagePath = savedImagePath,
        )
        val id = dao.insert(entry)
        val saved = entry.copy(id = id)

        s.exportFolderUri?.let { uriStr ->
            csvExporter.appendFoodEntry(Uri.parse(uriStr), saved)
        }
        return saved
    }

    suspend fun addManual(draft: FoodEntry): FoodEntry {
        val now = System.currentTimeMillis()
        val entry = draft.copy(
            timestampEpoch = now,
            lastModifiedEpoch = now,
            modelUsed = "manual",
            confidence = "user",
            imagePath = null,
        )
        val id = dao.insert(entry)
        val saved = entry.copy(id = id)
        settings.current().exportFolderUri?.let { uriStr ->
            csvExporter.appendFoodEntry(Uri.parse(uriStr), saved)
        }
        return saved
    }

    suspend fun update(updated: FoodEntry): FoodEntry {
        val bumped = updated.copy(lastModifiedEpoch = System.currentTimeMillis())
        dao.update(bumped)

        settings.current().exportFolderUri?.let { uriStr ->
            csvExporter.appendFoodEntry(Uri.parse(uriStr), bumped)
        }
        return bumped
    }

    suspend fun delete(entry: FoodEntry) {
        dao.delete(entry)
        settings.current().exportFolderUri?.let { uriStr ->
            csvExporter.appendFoodDelete(Uri.parse(uriStr), entry.copy(lastModifiedEpoch = System.currentTimeMillis()))
        }
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
}

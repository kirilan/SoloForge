package com.kbul.spicycrab.data.csv

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.kbul.spicycrab.data.db.entities.FoodEntry
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.db.entities.WorkoutSession
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CsvExporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    suspend fun appendFoodEntry(folderUri: Uri, entry: FoodEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, folderUri)
                    ?: error("Cannot open folder")
                val file = tree.findFile(FOOD_CSV) ?: tree.createFile("text/csv", FOOD_CSV)
                    ?: error("Cannot create CSV")
                val isNew = file.length() == 0L
                context.contentResolver.openOutputStream(file.uri, "wa")?.use { stream ->
                    if (isNew) {
                        stream.write(FOOD_HEADER.toByteArray())
                        stream.write("\n".toByteArray())
                    }
                    stream.write(entry.toCsvRow().toByteArray())
                    stream.write("\n".toByteArray())
                } ?: error("Cannot open output stream")
            }
        }

    suspend fun appendFoodDelete(folderUri: Uri, entry: FoodEntry): Result<Unit> =
        appendFoodEntry(folderUri, entry.copy(itemName = "[deleted]", confidence = "deleted"))

    suspend fun rewriteAll(
        folderUri: Uri,
        foodEntries: List<FoodEntry>,
        weightEntries: List<WeightEntry>,
        workoutSessions: List<WorkoutSession> = emptyList(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, folderUri) ?: error("Cannot open folder")

            tree.findFile(FOOD_CSV)?.delete()
            val foodFile = tree.createFile("text/csv", FOOD_CSV) ?: error("Cannot create food CSV")
            context.contentResolver.openOutputStream(foodFile.uri, "w")?.use { stream ->
                stream.write((FOOD_HEADER + "\n").toByteArray())
                foodEntries.forEach { stream.write((it.toCsvRow() + "\n").toByteArray()) }
            } ?: error("Cannot open food CSV stream")

            tree.findFile(WEIGHT_CSV)?.delete()
            val weightFile = tree.createFile("text/csv", WEIGHT_CSV) ?: error("Cannot create weight CSV")
            context.contentResolver.openOutputStream(weightFile.uri, "w")?.use { stream ->
                stream.write((WEIGHT_HEADER + "\n").toByteArray())
                weightEntries.forEach { stream.write((it.toCsvRow() + "\n").toByteArray()) }
            } ?: error("Cannot open weight CSV stream")

            tree.findFile(WORKOUT_CSV)?.delete()
            val workoutFile = tree.createFile("text/csv", WORKOUT_CSV) ?: error("Cannot create workout CSV")
            context.contentResolver.openOutputStream(workoutFile.uri, "w")?.use { stream ->
                stream.write((WORKOUT_HEADER + "\n").toByteArray())
                workoutSessions.forEach { stream.write((it.toCsvRow() + "\n").toByteArray()) }
            } ?: error("Cannot open workout CSV stream")
        }
    }

    suspend fun appendWorkoutEntry(folderUri: Uri, session: WorkoutSession): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, folderUri) ?: error("Cannot open folder")
                val file = tree.findFile(WORKOUT_CSV) ?: tree.createFile("text/csv", WORKOUT_CSV)
                    ?: error("Cannot create workout CSV")
                val isNew = file.length() == 0L
                context.contentResolver.openOutputStream(file.uri, "wa")?.use { stream ->
                    if (isNew) {
                        stream.write(WORKOUT_HEADER.toByteArray())
                        stream.write("\n".toByteArray())
                    }
                    stream.write(session.toCsvRow().toByteArray())
                    stream.write("\n".toByteArray())
                } ?: error("Cannot open output stream")
            }
        }

    suspend fun appendWorkoutDelete(folderUri: Uri, session: WorkoutSession): Result<Unit> =
        appendWorkoutEntry(folderUri, session.copy(notes = "[deleted]"))

    suspend fun appendWeightEntry(folderUri: Uri, entry: WeightEntry): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val tree = DocumentFile.fromTreeUri(context, folderUri) ?: error("Cannot open folder")
                val file = tree.findFile(WEIGHT_CSV) ?: tree.createFile("text/csv", WEIGHT_CSV)
                    ?: error("Cannot create CSV")
                val isNew = file.length() == 0L
                context.contentResolver.openOutputStream(file.uri, "wa")?.use { stream ->
                    if (isNew) {
                        stream.write(WEIGHT_HEADER.toByteArray())
                        stream.write("\n".toByteArray())
                    }
                    stream.write(entry.toCsvRow().toByteArray())
                    stream.write("\n".toByteArray())
                } ?: error("Cannot open output stream")
            }
        }

    suspend fun appendWeightDelete(folderUri: Uri, entry: WeightEntry): Result<Unit> =
        appendWeightEntry(folderUri, entry.copy(note = "[deleted]"))

    private fun WeightEntry.toCsvRow(): String {
        val ts = formatIso(timestampEpoch)
        val lastMod = formatIso(lastModifiedEpoch)
        return listOf(
            id.toString(),
            ts,
            lastMod,
            "%.2f".format(weightKg),
            csvEscape(note),
        ).joinToString(",")
    }

    private fun WorkoutSession.toCsvRow(): String {
        val start = formatIso(startEpoch)
        val end = endEpoch?.let { formatIso(it) } ?: ""
        val lastMod = formatIso(lastModifiedEpoch)
        return listOf(
            id.toString(),
            modeName,
            start,
            end,
            lastMod,
            totalSeconds.toString(),
            intervalSeconds.toString(),
            exerciseSeconds.toString(),
            restSeconds.toString(),
            csvEscape(notes),
        ).joinToString(",")
    }

    private fun FoodEntry.toCsvRow(): String {
        val ts = formatIso(timestampEpoch)
        val lastMod = formatIso(lastModifiedEpoch)
        return listOf(
            id.toString(),
            ts,
            lastMod,
            csvEscape(itemName),
            "%.1f".format(grams),
            "%.1f".format(kcal),
            "%.1f".format(proteinG),
            "%.1f".format(carbsG),
            "%.1f".format(fatG),
            "%.1f".format(fiberG),
            csvEscape(comment),
            csvEscape(modelUsed),
            csvEscape(confidence),
        ).joinToString(",")
    }

    private fun formatIso(epoch: Long): String =
        isoFormatter.format(Instant.ofEpochMilli(epoch).atZone(ZoneId.systemDefault()).toLocalDateTime())

    private fun csvEscape(value: String): String {
        if (value.isEmpty()) return ""
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuote) "\"$escaped\"" else escaped
    }

    private companion object {
        const val FOOD_CSV = "food_log.csv"
        const val WEIGHT_CSV = "weight_log.csv"
        const val WORKOUT_CSV = "workout_log.csv"
        const val FOOD_HEADER = "entry_id,timestamp_iso,last_modified_iso,item_name,grams,kcal,protein_g,carbs_g,fat_g,fiber_g,comment,model,confidence"
        const val WEIGHT_HEADER = "entry_id,timestamp_iso,last_modified_iso,weight_kg,note"
        const val WORKOUT_HEADER = "id,mode,start_iso,end_iso,last_modified_iso,total_seconds,interval_seconds,exercise_seconds,rest_seconds,notes"
    }
}

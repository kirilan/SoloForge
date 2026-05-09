package com.kbul.spicycrab.domain.weight

import android.net.Uri
import com.kbul.spicycrab.data.csv.CsvExporter
import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.prefs.SettingsRepo
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepository @Inject constructor(
    private val dao: WeightEntryDao,
    private val settings: SettingsRepo,
    private val csvExporter: CsvExporter,
) {

    fun observeAll(): Flow<List<WeightEntry>> = dao.observeAll()

    suspend fun add(weightKg: Double, note: String): WeightEntry {
        val now = System.currentTimeMillis()
        val entry = WeightEntry(timestampEpoch = now, lastModifiedEpoch = now, weightKg = weightKg, note = note)
        val id = dao.insert(entry)
        val saved = entry.copy(id = id)

        settings.current().exportFolderUri?.let { uriStr ->
            csvExporter.appendWeightEntry(Uri.parse(uriStr), saved)
        }
        return saved
    }

    suspend fun update(entry: WeightEntry) {
        val bumped = entry.copy(lastModifiedEpoch = System.currentTimeMillis())
        dao.update(bumped)
        settings.current().exportFolderUri?.let { uriStr ->
            csvExporter.appendWeightEntry(Uri.parse(uriStr), bumped)
        }
    }

    suspend fun delete(entry: WeightEntry) {
        dao.delete(entry)
        settings.current().exportFolderUri?.let { uriStr ->
            csvExporter.appendWeightDelete(Uri.parse(uriStr), entry.copy(lastModifiedEpoch = System.currentTimeMillis()))
        }
    }
    suspend fun mostRecent(): WeightEntry? = dao.mostRecent()

    fun toDisplayUnit(weightKg: Double, useKg: Boolean): Double =
        if (useKg) weightKg else weightKg * 2.20462262

    fun fromDisplayUnit(value: Double, useKg: Boolean): Double =
        if (useKg) value else value / 2.20462262
}

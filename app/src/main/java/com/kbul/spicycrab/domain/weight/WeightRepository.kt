package com.kbul.spicycrab.domain.weight

import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.domain.health.HealthConnectRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeightRepository @Inject constructor(
    private val dao: WeightEntryDao,
    private val healthConnect: HealthConnectRepository,
) {

    fun observeAll(): Flow<List<WeightEntry>> = dao.observeAll()

    suspend fun add(weightKg: Double, note: String, timestampEpoch: Long = System.currentTimeMillis()): WeightEntry {
        val entry = WeightEntry(
            timestampEpoch = timestampEpoch,
            lastModifiedEpoch = System.currentTimeMillis(),
            weightKg = weightKg,
            note = note,
        )
        return entry.copy(id = dao.insert(entry))
    }

    suspend fun update(entry: WeightEntry) {
        dao.update(entry.copy(lastModifiedEpoch = System.currentTimeMillis()))
    }

    suspend fun delete(entry: WeightEntry) {
        dao.delete(entry)
        healthConnect.onLocalWeightDeleted(entry)
    }

    suspend fun mostRecent(): WeightEntry? = dao.mostRecent()

    fun toDisplayUnit(weightKg: Double, useKg: Boolean): Double =
        if (useKg) weightKg else weightKg * 2.20462262

    fun fromDisplayUnit(value: Double, useKg: Boolean): Double =
        if (useKg) value else value / 2.20462262
}

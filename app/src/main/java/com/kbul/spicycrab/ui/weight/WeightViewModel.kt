package com.kbul.spicycrab.ui.weight

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.entities.WeightEntry
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.weight.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class WeightRange(@param:StringRes val labelRes: Int, val days: Int?) {
    SEVEN(R.string.weight_range_7d, 7), THIRTY(R.string.weight_range_30d, 30), ALL(R.string.weight_range_all, null)
}

data class WeightUiState(
    val entries: List<WeightEntry> = emptyList(),
    val useKg: Boolean = true,
    val range: WeightRange = WeightRange.THIRTY,
    val showLogSheet: Boolean = false,
    val editingId: Long? = null,
)

@HiltViewModel
class WeightViewModel @Inject constructor(
    private val repository: WeightRepository,
    settings: SettingsRepo,
) : ViewModel() {

    private val rangeFlow = MutableStateFlow(WeightRange.THIRTY)
    private val sheetFlow = MutableStateFlow<Long?>(null)
    private val showSheet = MutableStateFlow(false)

    val state: StateFlow<WeightUiState> = combine(
        repository.observeAll(),
        settings.settings.map { it.weightUnitKg },
        rangeFlow,
        showSheet,
        sheetFlow,
    ) { entries, kg, range, show, editing ->
        WeightUiState(
            entries = entries,
            useKg = kg,
            range = range,
            showLogSheet = show,
            editingId = editing,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUiState())

    fun openLogSheet(editing: WeightEntry? = null) {
        sheetFlow.value = editing?.id
        showSheet.value = true
    }

    fun dismissSheet() {
        showSheet.value = false
        sheetFlow.value = null
    }

    fun saveWeight(displayValue: Double, note: String, timestampEpoch: Long) {
        viewModelScope.launch {
            val kg = repository.fromDisplayUnit(displayValue, state.value.useKg)
            val editing = state.value.editingId
            if (editing != null) {
                val target = state.value.entries.firstOrNull { it.id == editing } ?: return@launch
                repository.update(target.copy(weightKg = kg, note = note, timestampEpoch = timestampEpoch))
            } else {
                repository.add(kg, note, timestampEpoch)
            }
            dismissSheet()
        }
    }

    fun deleteEntry(entry: WeightEntry) {
        viewModelScope.launch { repository.delete(entry) }
    }

    fun setRange(range: WeightRange) {
        rangeFlow.value = range
    }

    fun toDisplay(kg: Double): Double = repository.toDisplayUnit(kg, state.value.useKg)
}

package com.kbul.spicycrab.ui.food

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.db.entities.FoodEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kbul.spicycrab.data.db.entities.MealPreset
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.nutrition.FoodRepository
import com.kbul.spicycrab.domain.nutrition.NutritionEstimate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface FoodUiMode {
    data object List : FoodUiMode
    data object Capture : FoodUiMode
    data class Analyze(val imageFile: File?) : FoodUiMode
}

data class AnalyzeState(
    val comment: String = "",
    val isLoading: Boolean = false,
    val estimate: NutritionEstimate? = null,
    val error: String? = null,
    val savedOk: Boolean = false,
)

data class EditingState(
    val entry: FoodEntry,
    val isReanalyzing: Boolean = false,
    val reanalyzeError: String? = null,
    val reanalyzeStamp: Long = 0L,
)

@HiltViewModel
class FoodViewModel @Inject constructor(
    private val repository: FoodRepository,
    settings: SettingsRepo,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val aiEnabled: StateFlow<Boolean> = settings.settings.map { it.aiFeaturesEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val _mode = MutableStateFlow<FoodUiMode>(FoodUiMode.List)
    val mode: StateFlow<FoodUiMode> = _mode.asStateFlow()

    private val _analyze = MutableStateFlow(AnalyzeState())
    val analyze: StateFlow<AnalyzeState> = _analyze.asStateFlow()

    private val _editing = MutableStateFlow<EditingState?>(null)
    val editing: StateFlow<EditingState?> = _editing.asStateFlow()

    private val _manualOpen = MutableStateFlow(false)
    val manualOpen: StateFlow<Boolean> = _manualOpen.asStateFlow()

    val entries: StateFlow<List<FoodEntry>> = repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val presets: StateFlow<List<MealPreset>> = repository.observePresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun goToCapture() {
        _mode.value = FoodUiMode.Capture
    }

    fun startTextEntry() {
        _analyze.value = AnalyzeState()
        _mode.value = FoodUiMode.Analyze(imageFile = null)
    }

    fun onCaptured(file: File) {
        _analyze.value = AnalyzeState()
        _mode.value = FoodUiMode.Analyze(file)
    }

    fun onCaptureCancelled() {
        _mode.value = FoodUiMode.List
    }

    fun onCommentChange(value: String) {
        _analyze.value = _analyze.value.copy(comment = value)
    }

    fun analyze() {
        val current = _mode.value
        if (current !is FoodUiMode.Analyze) return
        val file = current.imageFile
        val comment = _analyze.value.comment
        if (file == null && comment.isBlank()) return
        _analyze.value = _analyze.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = if (file != null) repository.analyze(file, comment) else repository.analyzeText(comment)
            _analyze.value = result.fold(
                onSuccess = { _analyze.value.copy(isLoading = false, estimate = it) },
                onFailure = { _analyze.value.copy(isLoading = false, error = it.message ?: context.getString(R.string.error_analysis_failed)) },
            )
        }
    }

    fun updateEstimate(transform: (NutritionEstimate) -> NutritionEstimate) {
        val cur = _analyze.value.estimate ?: return
        _analyze.value = _analyze.value.copy(estimate = transform(cur))
    }

    fun saveEntry() {
        val current = _mode.value
        val est = _analyze.value.estimate ?: return
        val imageFile = (current as? FoodUiMode.Analyze)?.imageFile
        viewModelScope.launch {
            runCatching { repository.save(est, _analyze.value.comment, imageFile) }
                .onSuccess {
                    _analyze.value = AnalyzeState(savedOk = true)
                    _mode.value = FoodUiMode.List
                }
                .onFailure {
                    _analyze.value = _analyze.value.copy(error = it.message ?: context.getString(R.string.error_save_failed))
                }
        }
    }

    fun cancelAnalyze() {
        _analyze.value = AnalyzeState()
        _mode.value = FoodUiMode.List
    }

    fun openEdit(entry: FoodEntry) {
        _editing.value = EditingState(entry = entry)
    }

    fun dismissEdit() {
        _editing.value = null
    }

    fun saveEdit(updated: FoodEntry) {
        viewModelScope.launch {
            runCatching { repository.update(updated) }
                .onSuccess { _editing.value = null }
        }
    }

    fun deleteEntry(entry: FoodEntry) {
        viewModelScope.launch {
            repository.delete(entry)
            _editing.value = null
        }
    }

    fun logPreset(preset: MealPreset) {
        viewModelScope.launch { repository.logPreset(preset) }
    }

    fun deletePreset(preset: MealPreset) {
        viewModelScope.launch { repository.deletePreset(preset) }
    }

    fun saveAsPreset(source: FoodEntry) {
        if (source.itemName.isBlank()) return
        viewModelScope.launch { repository.saveAsPreset(source, source.itemName) }
    }

    fun openManual() { _manualOpen.value = true }
    fun dismissManual() { _manualOpen.value = false }
    fun saveManual(draft: FoodEntry) {
        viewModelScope.launch {
            runCatching { repository.addManual(draft) }
                .onSuccess { _manualOpen.value = false }
        }
    }

    fun reanalyzeEdit(updatedComment: String) {
        val cur = _editing.value ?: return
        val path = cur.entry.imagePath
        if (path == null && updatedComment.isBlank()) return
        _editing.value = cur.copy(isReanalyzing = true, reanalyzeError = null)
        viewModelScope.launch {
            val result = if (path != null) {
                repository.analyze(File(path), updatedComment)
            } else {
                repository.analyzeText(updatedComment)
            }
            _editing.value = result.fold(
                onSuccess = { est ->
                    EditingState(
                        entry = cur.entry.copy(
                            itemName = est.itemName,
                            grams = est.grams,
                            kcal = est.kcal,
                            proteinG = est.proteinG,
                            carbsG = est.carbsG,
                            fatG = est.fatG,
                            fiberG = est.fiberG,
                            confidence = est.confidence,
                            comment = updatedComment,
                            modelUsed = est.modelUsed,
                        ),
                        isReanalyzing = false,
                        reanalyzeStamp = System.currentTimeMillis(),
                    )
                },
                onFailure = { cur.copy(isReanalyzing = false, reanalyzeError = it.message ?: context.getString(R.string.error_reanalysis_failed)) },
            )
        }
    }
}

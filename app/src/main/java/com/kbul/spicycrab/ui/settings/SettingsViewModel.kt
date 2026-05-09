package com.kbul.spicycrab.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.kbul.spicycrab.data.csv.CsvExporter
import com.kbul.spicycrab.data.db.dao.FoodEntryDao
import com.kbul.spicycrab.data.db.dao.WeightEntryDao
import com.kbul.spicycrab.data.db.dao.WorkoutSessionDao
import com.kbul.spicycrab.data.prefs.AppSettings
import com.kbul.spicycrab.data.prefs.NutritionGoals
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.notifications.ReminderScheduler
import java.time.DayOfWeek
import java.time.LocalTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepo,
    private val keyStore: SecureKeyStore,
    private val reminderScheduler: ReminderScheduler,
    private val csvExporter: CsvExporter,
    private val foodEntryDao: FoodEntryDao,
    private val weightEntryDao: WeightEntryDao,
    private val workoutSessionDao: WorkoutSessionDao,
) : ViewModel() {

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()

    val state: StateFlow<AppSettings?> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _hasKey = MutableStateFlow(keyStore.hasOpenRouterKey())
    val hasKey: StateFlow<Boolean> = _hasKey.asStateFlow()

    fun setApiKey(value: String) {
        keyStore.setOpenRouterKey(value.takeIf { it.isNotBlank() })
        _hasKey.value = keyStore.hasOpenRouterKey()
    }

    fun clearApiKey() {
        keyStore.setOpenRouterKey(null)
        _hasKey.value = false
    }

    fun setExportFolder(uri: String?) = viewModelScope.launch { settings.setExportFolderUri(uri) }

    fun setSavePhotoLocally(value: Boolean) = viewModelScope.launch { settings.setSavePhotoLocally(value) }

    fun setWeightUnitKg(value: Boolean) = viewModelScope.launch { settings.setWeightUnitKg(value) }

    fun setGoals(goals: NutritionGoals) = viewModelScope.launch { settings.setGoals(goals) }

    fun setWeighInEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setWeighInEnabled(enabled)
        if (enabled) {
            val s = settings.current()
            reminderScheduler.scheduleWeeklyWeighIn(
                DayOfWeek.of(s.weighInDayOfWeek),
                LocalTime.of(s.weighInHour, s.weighInMinute),
            )
        } else {
            reminderScheduler.cancelWeeklyWeighIn()
        }
    }

    fun setWeighInTime(dayOfWeek: Int, hour: Int, minute: Int) = viewModelScope.launch {
        settings.setWeighInTime(dayOfWeek, hour, minute)
        if (settings.current().weighInEnabled) {
            reminderScheduler.scheduleWeeklyWeighIn(
                DayOfWeek.of(dayOfWeek),
                LocalTime.of(hour, minute),
            )
        }
    }

    fun setDefaultFastingMode(name: String) = viewModelScope.launch {
        settings.setDefaultFastingMode(name)
    }

    fun setAlmostThereEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setAlmostThereEnabled(enabled)
        if (!enabled) reminderScheduler.cancelAlmostThere()
    }

    fun setEatingWindowClosingEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setEatingWindowClosingEnabled(enabled)
        if (!enabled) reminderScheduler.cancelEatingWindowClosing()
    }

    fun setShowFastingTab(value: Boolean) = viewModelScope.launch { settings.setShowFastingTab(value) }
    fun setShowFoodTab(value: Boolean) = viewModelScope.launch { settings.setShowFoodTab(value) }
    fun setShowWeightTab(value: Boolean) = viewModelScope.launch { settings.setShowWeightTab(value) }
    fun setShowWorkoutTab(value: Boolean) = viewModelScope.launch { settings.setShowWorkoutTab(value) }

    fun exportAll() {
        viewModelScope.launch {
            val s = settings.current()
            val uriStr = s.exportFolderUri
            if (uriStr == null) {
                _exportMessage.value = "Set an export folder first."
                return@launch
            }
            val foods = foodEntryDao.observeAll().first()
            val weights = weightEntryDao.observeAll().first()
            val workouts = workoutSessionDao.observeAll().first()
            val result = csvExporter.rewriteAll(Uri.parse(uriStr), foods, weights, workouts)
            _exportMessage.value = result.fold(
                onSuccess = { "Exported ${foods.size} meals, ${weights.size} weight entries, ${workouts.size} workouts." },
                onFailure = { "Export failed: ${it.message}" },
            )
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }
}

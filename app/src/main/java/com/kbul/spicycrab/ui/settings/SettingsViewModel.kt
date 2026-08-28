package com.kbul.spicycrab.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.kbul.spicycrab.R
import com.kbul.spicycrab.data.backup.BackupManager
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kbul.spicycrab.data.prefs.AppSettings
import com.kbul.spicycrab.data.prefs.NutritionGoals
import com.kbul.spicycrab.data.prefs.SecureKeyStore
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.health.HealthConnectRepository
import com.kbul.spicycrab.domain.fasting.FastingRepository
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
    private val fastingRepository: FastingRepository,
    private val backupManager: BackupManager,
    private val healthConnect: HealthConnectRepository,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    val healthConnectAvailable: Boolean = healthConnect.isAvailable()
    val healthImportPermissions: Set<String> get() = healthConnect.importPermissions
    val healthExportPermissions: Set<String> get() = healthConnect.exportPermissions
    fun healthPermissionContract() = healthConnect.permissionRequestContract()

    // A direction is on if any of its permissions were granted (subset grants are fine — we sync what we have).
    fun onHealthImportResult(granted: Set<String>) = viewModelScope.launch {
        val enabled = granted.any { it in healthImportPermissions }
        settings.setHealthImportEnabled(enabled)
        if (enabled) healthConnect.sync()
    }

    fun onHealthExportResult(granted: Set<String>) = viewModelScope.launch {
        val enabled = granted.any { it in healthExportPermissions }
        settings.setHealthExportEnabled(enabled)
        if (enabled) healthConnect.sync()
    }

    fun setHealthImportEnabled(value: Boolean) = viewModelScope.launch { settings.setHealthImportEnabled(value) }
    fun setHealthExportEnabled(value: Boolean) = viewModelScope.launch { settings.setHealthExportEnabled(value) }

    fun syncHealthNow() = viewModelScope.launch { healthConnect.sync() }

    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage.asStateFlow()
    val autoBackupStatus = backupManager.autoBackupStatus

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

    fun setAiFeaturesEnabled(value: Boolean) = viewModelScope.launch { settings.setAiFeaturesEnabled(value) }

    fun setFoodAnalysisModel(token: String) = viewModelScope.launch {
        settings.setFoodAnalysisModel(token)
    }

    fun setFoodAnalysisModelCustomId(modelId: String) = viewModelScope.launch {
        settings.setFoodAnalysisModelCustomId(modelId)
    }

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
        fastingRepository.syncReminders()
    }

    fun setEatingWindowClosingEnabled(enabled: Boolean) = viewModelScope.launch {
        settings.setEatingWindowClosingEnabled(enabled)
        fastingRepository.syncReminders()
    }

    fun setShowFastingTab(value: Boolean) = viewModelScope.launch { settings.setShowFastingTab(value) }
    fun setShowFoodTab(value: Boolean) = viewModelScope.launch { settings.setShowFoodTab(value) }
    fun setShowWeightTab(value: Boolean) = viewModelScope.launch { settings.setShowWeightTab(value) }
    fun setShowWorkoutTab(value: Boolean) = viewModelScope.launch { settings.setShowWorkoutTab(value) }

    fun exportBackup(uri: Uri) {
        viewModelScope.launch {
            _exportMessage.value = backupManager.exportTo(uri).fold(
                onSuccess = { context.getString(R.string.settings_msg_backup_exported) },
                onFailure = { context.getString(R.string.settings_msg_export_failed, it.message) },
            )
        }
    }

    fun importBackup(uri: Uri, merge: Boolean) {
        viewModelScope.launch {
            _exportMessage.value = backupManager.importFrom(uri, merge).fold(
                onSuccess = { summary ->
                    when {
                        summary.warning -> context.getString(R.string.settings_msg_import_completed_warning)
                        summary.replaced -> context.getString(R.string.settings_msg_backup_restored, summary.added)
                        else -> context.resources.getQuantityString(R.plurals.settings_msg_merged, summary.added, summary.added)
                    }
                },
                onFailure = { context.getString(R.string.settings_msg_import_failed, it.message) },
            )
        }
    }

    fun clearExportMessage() {
        _exportMessage.value = null
    }
}

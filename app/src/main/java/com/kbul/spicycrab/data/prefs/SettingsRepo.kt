package com.kbul.spicycrab.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

@Serializable
data class NutritionGoals(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
)

@Serializable
data class AppSettings(
    val exportFolderUri: String?,
    val savePhotoLocally: Boolean,
    val aiFeaturesEnabled: Boolean,
    val weightUnitKg: Boolean,
    val goals: NutritionGoals,
    val weighInEnabled: Boolean,
    val weighInDayOfWeek: Int,
    val weighInHour: Int,
    val weighInMinute: Int,
    val defaultFastingModeName: String,
    val almostThereEnabled: Boolean,
    val eatingWindowClosingEnabled: Boolean,
    val showFastingTab: Boolean,
    val showFoodTab: Boolean,
    val showWeightTab: Boolean,
    val showWorkoutTab: Boolean,
    val onboardingComplete: Boolean,
    // Defaults keep pre-0.4.0 backup files decodable.
    val healthImportEnabled: Boolean = false,
    val healthExportEnabled: Boolean = false,
    val healthLastSyncEpoch: Long = 0L,
)

@Singleton
class SettingsRepo @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { it.toAppSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setExportFolderUri(uri: String?) = update {
        if (uri == null) it.remove(KEY_EXPORT_URI) else it[KEY_EXPORT_URI] = uri
    }
    suspend fun setSavePhotoLocally(value: Boolean) = update { it[KEY_SAVE_PHOTO] = value }
    suspend fun setAiFeaturesEnabled(value: Boolean) = update { it[KEY_AI_ENABLED] = value }
    suspend fun setWeightUnitKg(value: Boolean) = update { it[KEY_WEIGHT_KG] = value }
    suspend fun setDefaultFastingMode(name: String) = update { it[KEY_DEFAULT_MODE] = name }
    suspend fun setAlmostThereEnabled(value: Boolean) = update { it[KEY_REMIND_ALMOST] = value }
    suspend fun setEatingWindowClosingEnabled(value: Boolean) = update { it[KEY_REMIND_WINDOW] = value }
    suspend fun setShowFastingTab(value: Boolean) = update { it[KEY_TAB_FAST] = value }
    suspend fun setShowFoodTab(value: Boolean) = update { it[KEY_TAB_FOOD] = value }
    suspend fun setShowWeightTab(value: Boolean) = update { it[KEY_TAB_WEIGHT] = value }
    suspend fun setShowWorkoutTab(value: Boolean) = update { it[KEY_TAB_WORKOUT] = value }
    suspend fun setOnboardingComplete(value: Boolean) = update { it[KEY_ONBOARDING_COMPLETE] = value }
    suspend fun setHealthImportEnabled(value: Boolean) = update { it[KEY_HEALTH_IMPORT] = value }
    suspend fun setHealthExportEnabled(value: Boolean) = update { it[KEY_HEALTH_EXPORT] = value }
    suspend fun setHealthLastSync(epoch: Long) = update { it[KEY_HEALTH_LAST_SYNC] = epoch }

    // HC change token is device-specific operational state — not observed by UI, not backed up.
    suspend fun healthChangeCursor(): Pair<String, String>? {
        val prefs = context.settingsDataStore.data.first()
        val token = prefs[KEY_HEALTH_TOKEN] ?: return null
        val typeKey = prefs[KEY_HEALTH_TOKEN_TYPES] ?: return null
        return token to typeKey
    }

    suspend fun setHealthChangeCursor(token: String, typeKey: String) = update {
        it[KEY_HEALTH_TOKEN] = token
        it[KEY_HEALTH_TOKEN_TYPES] = typeKey
    }

    suspend fun setWeighInEnabled(value: Boolean) = update { it[KEY_WEIGH_ENABLED] = value }
    suspend fun setWeighInTime(dayOfWeek: Int, hour: Int, minute: Int) = update {
        it[KEY_WEIGH_DAY] = dayOfWeek
        it[KEY_WEIGH_HOUR] = hour
        it[KEY_WEIGH_MIN] = minute
    }
    suspend fun setGoals(goals: NutritionGoals) = update {
        it[KEY_GOAL_KCAL] = goals.kcal
        it[KEY_GOAL_PROTEIN] = goals.proteinG
        it[KEY_GOAL_CARBS] = goals.carbsG
        it[KEY_GOAL_FAT] = goals.fatG
        it[KEY_GOAL_FIBER] = goals.fiberG
    }

    // exportFolderUri stays local: SAF tree permissions don't transfer between installs.
    suspend fun applyBackup(s: AppSettings) = update {
        it[KEY_SAVE_PHOTO] = s.savePhotoLocally
        it[KEY_AI_ENABLED] = s.aiFeaturesEnabled
        it[KEY_WEIGHT_KG] = s.weightUnitKg
        it[KEY_GOAL_KCAL] = s.goals.kcal
        it[KEY_GOAL_PROTEIN] = s.goals.proteinG
        it[KEY_GOAL_CARBS] = s.goals.carbsG
        it[KEY_GOAL_FAT] = s.goals.fatG
        it[KEY_GOAL_FIBER] = s.goals.fiberG
        it[KEY_WEIGH_ENABLED] = s.weighInEnabled
        it[KEY_WEIGH_DAY] = s.weighInDayOfWeek
        it[KEY_WEIGH_HOUR] = s.weighInHour
        it[KEY_WEIGH_MIN] = s.weighInMinute
        it[KEY_DEFAULT_MODE] = s.defaultFastingModeName
        it[KEY_REMIND_ALMOST] = s.almostThereEnabled
        it[KEY_REMIND_WINDOW] = s.eatingWindowClosingEnabled
        it[KEY_TAB_FAST] = s.showFastingTab
        it[KEY_TAB_FOOD] = s.showFoodTab
        it[KEY_TAB_WEIGHT] = s.showWeightTab
        it[KEY_TAB_WORKOUT] = s.showWorkoutTab
        it[KEY_ONBOARDING_COMPLETE] = s.onboardingComplete
        // Toggles restore; healthLastSyncEpoch/token stay local (device-specific, like exportFolderUri).
        it[KEY_HEALTH_IMPORT] = s.healthImportEnabled
        it[KEY_HEALTH_EXPORT] = s.healthExportEnabled
    }

    private suspend fun update(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        exportFolderUri = this[KEY_EXPORT_URI],
        savePhotoLocally = this[KEY_SAVE_PHOTO] ?: false,
        aiFeaturesEnabled = this[KEY_AI_ENABLED] ?: true,
        weightUnitKg = this[KEY_WEIGHT_KG] ?: true,
        goals = NutritionGoals(
            kcal = this[KEY_GOAL_KCAL] ?: 2000,
            proteinG = this[KEY_GOAL_PROTEIN] ?: 150,
            carbsG = this[KEY_GOAL_CARBS] ?: 220,
            fatG = this[KEY_GOAL_FAT] ?: 65,
            fiberG = this[KEY_GOAL_FIBER] ?: 30,
        ),
        weighInEnabled = this[KEY_WEIGH_ENABLED] ?: false,
        weighInDayOfWeek = this[KEY_WEIGH_DAY] ?: 1,
        weighInHour = this[KEY_WEIGH_HOUR] ?: 8,
        weighInMinute = this[KEY_WEIGH_MIN] ?: 0,
        defaultFastingModeName = this[KEY_DEFAULT_MODE] ?: "SIXTEEN_EIGHT",
        almostThereEnabled = this[KEY_REMIND_ALMOST] ?: true,
        eatingWindowClosingEnabled = this[KEY_REMIND_WINDOW] ?: true,
        showFastingTab = this[KEY_TAB_FAST] ?: true,
        showFoodTab = this[KEY_TAB_FOOD] ?: true,
        showWeightTab = this[KEY_TAB_WEIGHT] ?: true,
        showWorkoutTab = this[KEY_TAB_WORKOUT] ?: true,
        onboardingComplete = this[KEY_ONBOARDING_COMPLETE] ?: false,
        healthImportEnabled = this[KEY_HEALTH_IMPORT] ?: false,
        healthExportEnabled = this[KEY_HEALTH_EXPORT] ?: false,
        healthLastSyncEpoch = this[KEY_HEALTH_LAST_SYNC] ?: 0L,
    )

    private companion object {
        val KEY_EXPORT_URI = stringPreferencesKey("export_folder_uri")
        val KEY_SAVE_PHOTO = booleanPreferencesKey("save_photo_locally")
        val KEY_AI_ENABLED = booleanPreferencesKey("ai_features_enabled")
        val KEY_WEIGHT_KG = booleanPreferencesKey("weight_unit_kg")
        val KEY_GOAL_KCAL = intPreferencesKey("goal_kcal")
        val KEY_GOAL_PROTEIN = intPreferencesKey("goal_protein")
        val KEY_GOAL_CARBS = intPreferencesKey("goal_carbs")
        val KEY_GOAL_FAT = intPreferencesKey("goal_fat")
        val KEY_GOAL_FIBER = intPreferencesKey("goal_fiber")
        val KEY_WEIGH_ENABLED = booleanPreferencesKey("weigh_in_enabled")
        val KEY_WEIGH_DAY = intPreferencesKey("weigh_in_day")
        val KEY_WEIGH_HOUR = intPreferencesKey("weigh_in_hour")
        val KEY_WEIGH_MIN = intPreferencesKey("weigh_in_min")
        val KEY_DEFAULT_MODE = stringPreferencesKey("default_fasting_mode")
        val KEY_REMIND_ALMOST = booleanPreferencesKey("remind_almost_there")
        val KEY_REMIND_WINDOW = booleanPreferencesKey("remind_window_closing")
        val KEY_TAB_FAST = booleanPreferencesKey("tab_fast_visible")
        val KEY_TAB_FOOD = booleanPreferencesKey("tab_food_visible")
        val KEY_TAB_WEIGHT = booleanPreferencesKey("tab_weight_visible")
        val KEY_TAB_WORKOUT = booleanPreferencesKey("tab_workout_visible")
        val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val KEY_HEALTH_IMPORT = booleanPreferencesKey("health_import_enabled")
        val KEY_HEALTH_EXPORT = booleanPreferencesKey("health_export_enabled")
        val KEY_HEALTH_LAST_SYNC = longPreferencesKey("health_last_sync")
        val KEY_HEALTH_TOKEN = stringPreferencesKey("health_change_token")
        val KEY_HEALTH_TOKEN_TYPES = stringPreferencesKey("health_change_token_types")
    }
}

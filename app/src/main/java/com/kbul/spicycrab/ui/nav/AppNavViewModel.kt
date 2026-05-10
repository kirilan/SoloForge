package com.kbul.spicycrab.ui.nav

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbul.spicycrab.data.prefs.SettingsRepo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TabVisibility(
    val fasting: Boolean = true,
    val food: Boolean = true,
    val weight: Boolean = true,
    val workout: Boolean = true,
)

@HiltViewModel
class AppNavViewModel @Inject constructor(
    private val settings: SettingsRepo,
) : ViewModel() {
    val visibility: StateFlow<TabVisibility> = settings.settings
        .map { TabVisibility(it.showFastingTab, it.showFoodTab, it.showWeightTab, it.showWorkoutTab) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TabVisibility())

    val onboardingComplete: StateFlow<Boolean?> = settings.settings
        .map { it.onboardingComplete }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun completeOnboarding() {
        viewModelScope.launch {
            settings.setOnboardingComplete(true)
        }
    }
}

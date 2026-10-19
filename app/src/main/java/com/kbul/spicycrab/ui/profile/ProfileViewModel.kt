package com.kbul.spicycrab.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbul.spicycrab.data.prefs.BodyProfile
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.profile.GoalCalculator
import com.kbul.spicycrab.domain.weight.WeightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Year
import javax.inject.Inject

data class ProfileUiState(
    val profile: BodyProfile,
    val useKg: Boolean,
    val latestWeightKg: Double?,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val settings: SettingsRepo,
    private val weightRepository: WeightRepository,
) : ViewModel() {

    val state: StateFlow<ProfileUiState?> = combine(
        settings.settings,
        weightRepository.observeAll(),
    ) { s, weights ->
        ProfileUiState(s.profile, s.weightUnitKg, weights.firstOrNull()?.weightKg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setUseKg(value: Boolean) = viewModelScope.launch { settings.setWeightUnitKg(value) }

    /** [newWeightKg] is logged as a weigh-in; goals are replaced only when [applyGoals] and a weight is known. */
    fun save(profile: BodyProfile, newWeightKg: Double?, applyGoals: Boolean, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            settings.setProfile(profile)
            newWeightKg?.let { weightRepository.add(it, note = "") }
            val weight = newWeightKg ?: state.value?.latestWeightKg
            if (applyGoals && weight != null) {
                settings.setGoals(GoalCalculator.suggest(weight, profile, Year.now().value).goals)
            }
            onSaved()
        }
    }
}

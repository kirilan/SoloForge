package com.kbul.spicycrab.ui.fasting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kbul.spicycrab.data.db.entities.FastSession
import com.kbul.spicycrab.data.prefs.SettingsRepo
import com.kbul.spicycrab.domain.fasting.FastingMode
import com.kbul.spicycrab.domain.fasting.FastingRepository
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FastingUiState(
    val active: FastSession?,
    val selectedMode: FastingMode,
    val nowMillis: Long,
    val history: List<FastSession> = emptyList(),
)

@HiltViewModel
class FastingViewModel @Inject constructor(
    private val repository: FastingRepository,
    settings: SettingsRepo,
) : ViewModel() {

    private val selectedMode = MutableStateFlow(FastingMode.SIXTEEN_EIGHT)
    private var userPickedMode = false

    init {
        viewModelScope.launch {
            settings.settings.map { FastingMode.fromName(it.defaultFastingModeName) }.collect { mode ->
                if (!userPickedMode) selectedMode.value = mode
            }
        }
    }

    private val ticker: kotlinx.coroutines.flow.Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    val uiState: StateFlow<FastingUiState> = combine(
        repository.observeActive(),
        repository.observeAll(),
        selectedMode,
        ticker,
    ) { active, all, mode, now ->
        FastingUiState(
            active = active,
            selectedMode = mode,
            nowMillis = now,
            history = all.filter { it.endEpoch != null },
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FastingUiState(null, FastingMode.SIXTEEN_EIGHT, System.currentTimeMillis()),
    )

    private val _editing = MutableStateFlow<FastSession?>(null)
    val editing: StateFlow<FastSession?> = _editing.asStateFlow()

    fun openEdit(session: FastSession) { _editing.value = session }
    fun dismissEdit() { _editing.value = null }

    fun saveEdit(updated: FastSession) {
        viewModelScope.launch {
            if (repository.updateSession(updated)) {
                _editing.value = null
            }
        }
    }

    fun deleteSession(session: FastSession) {
        viewModelScope.launch {
            repository.deleteSession(session)
            _editing.value = null
        }
    }

    fun cancelActive() {
        viewModelScope.launch {
            uiState.value.active?.let { repository.deleteSession(it) }
        }
    }

    fun onModeSelected(mode: FastingMode) {
        userPickedMode = true
        selectedMode.value = mode
    }

    fun startFast() {
        viewModelScope.launch {
            repository.startFast(selectedMode.value)
        }
    }

    fun stopFast() {
        viewModelScope.launch {
            val active = uiState.value.active ?: return@launch
            repository.stopFast(active)
        }
    }
}

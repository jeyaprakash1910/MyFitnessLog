package com.myfitnesslog.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.settings.data.SettingsRepository
import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable state for the Settings screen. */
data class SettingsUiState(
    val previousWorkoutValues: PreviousWorkoutValues = PreviousWorkoutValues.DEFAULT,
)

/**
 * ViewModel for the Settings screen. A thin orchestration layer over
 * [SettingsRepository]: it observes persisted settings for display and writes the
 * user's selection back. DataStore is the source of truth, so a write re-emits and
 * updates the UI without any local mirror.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> =
        settingsRepository.previousWorkoutValues
            .map { SettingsUiState(previousWorkoutValues = it) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(),
            )

    fun onPreviousWorkoutValuesSelected(value: PreviousWorkoutValues) {
        viewModelScope.launch { settingsRepository.setPreviousWorkoutValues(value) }
    }
}

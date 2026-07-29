package com.myfitnesslog.feature.settings.data

import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues
import kotlinx.coroutines.flow.Flow

/**
 * Application settings boundary. Settings are persisted with DataStore and survive
 * process death and app restarts. The repository exposes only what consumers need
 * today — the "Previous Workout Values" strategy — and grows as settings are added.
 */
interface SettingsRepository {

    /** The current PREVIOUS-column lookup strategy; emits [PreviousWorkoutValues.DEFAULT] until set. */
    val previousWorkoutValues: Flow<PreviousWorkoutValues>

    /** Persist the PREVIOUS-column lookup strategy. */
    suspend fun setPreviousWorkoutValues(value: PreviousWorkoutValues)
}

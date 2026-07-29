package com.myfitnesslog.feature.settings.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed [SettingsRepository]. The enum is stored by its stable [Enum.name];
 * an absent or unrecognised value maps to [PreviousWorkoutValues.DEFAULT], so a fresh
 * install and a forward/backward-incompatible value both degrade to the default rather
 * than crashing.
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val previousWorkoutValues: Flow<PreviousWorkoutValues> =
        dataStore.data.map { prefs ->
            PreviousWorkoutValues.fromStorage(prefs[PREVIOUS_WORKOUT_VALUES])
        }

    override suspend fun setPreviousWorkoutValues(value: PreviousWorkoutValues) {
        dataStore.edit { prefs -> prefs[PREVIOUS_WORKOUT_VALUES] = value.name }
    }

    private companion object {
        val PREVIOUS_WORKOUT_VALUES = stringPreferencesKey("previous_workout_values")
    }
}

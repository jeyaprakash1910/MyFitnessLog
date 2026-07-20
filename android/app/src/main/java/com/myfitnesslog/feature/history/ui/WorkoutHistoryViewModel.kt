package com.myfitnesslog.feature.history.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepository
import com.myfitnesslog.feature.history.data.local.CompletedWorkoutSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ViewModel for the workout history list. Observes completed workouts (with their
 * exercise count) from the read-only [WorkoutHistoryRepository] and maps each into
 * a fully-formatted [WorkoutHistoryItem]. All derivation (duration, date, type
 * label) happens here, never in Compose. History is read-only, so this ViewModel
 * exposes no mutations.
 */
@HiltViewModel
class WorkoutHistoryViewModel @Inject constructor(
    repository: WorkoutHistoryRepository,
) : ViewModel() {

    val uiState: StateFlow<WorkoutHistoryUiState> =
        repository.observeCompletedWorkoutSummaries()
            .map { summaries ->
                if (summaries.isEmpty()) {
                    WorkoutHistoryUiState.Empty
                } else {
                    WorkoutHistoryUiState.Success(summaries.map { it.toItem() })
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = WorkoutHistoryUiState.Loading,
            )

    private fun CompletedWorkoutSummary.toItem(): WorkoutHistoryItem =
        WorkoutHistoryItem(
            id = session.id,
            date = formatWorkoutDate(session.startedAt),
            duration = formatCompletedDuration(session.startedAt, session.endedAt),
            typeLabel = workoutTypeLabel(session.routineId),
            exercisesLabel = exerciseCountLabel(exerciseCount),
            notesPreview = sanitizeNotes(session.notes),
        )
}

package com.myfitnesslog.feature.history.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepository
import com.myfitnesslog.feature.history.ui.WorkoutHistoryRoutes
import com.myfitnesslog.feature.history.ui.formatCompletedDuration
import com.myfitnesslog.feature.history.ui.formatRpe
import com.myfitnesslog.feature.history.ui.formatWeightReps
import com.myfitnesslog.feature.history.ui.formatWorkoutDate
import com.myfitnesslog.feature.history.ui.sanitizeNotes
import com.myfitnesslog.feature.history.ui.setCategoryLabel
import com.myfitnesslog.feature.history.ui.workoutTypeLabel
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the read-only workout detail screen. Combines a completed
 * session, its snapshotted exercises, and every performed set (all Room-backed
 * via the read-only [WorkoutHistoryRepository]) into an immutable state, grouping
 * sets under their exercise while preserving the captured exercise and set order.
 *
 * The screen is a historical record: this ViewModel exposes no mutations. Only
 * COMPLETED workouts resolve; anything else (discarded, in-progress, deleted)
 * maps to [WorkoutDetailUiState.NotFound].
 */
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: WorkoutHistoryRepository,
) : ViewModel() {

    private val sessionId: UUID =
        UUID.fromString(checkNotNull(savedStateHandle[WorkoutHistoryRoutes.ARG_SESSION_ID]))

    val uiState: StateFlow<WorkoutDetailUiState> =
        combine(
            repository.observeWorkout(sessionId),
            repository.observeExercises(sessionId),
            repository.observeSets(sessionId),
        ) { session, exercises, sets ->
            if (session == null) {
                WorkoutDetailUiState.NotFound
            } else {
                val setsByExercise = sets.groupBy { it.workoutExerciseId }
                WorkoutDetailUiState.Success(
                    date = formatWorkoutDate(session.startedAt),
                    duration = formatCompletedDuration(session.startedAt, session.endedAt),
                    typeLabel = workoutTypeLabel(session.routineId),
                    notes = sanitizeNotes(session.notes),
                    exercises = exercises.map { exercise ->
                        exercise.toRow(setsByExercise[exercise.id].orEmpty())
                    },
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkoutDetailUiState.Loading,
        )
}

private fun WorkoutExerciseEntity.toRow(sets: List<WorkoutSetEntity>): WorkoutDetailExerciseRow =
    WorkoutDetailExerciseRow(
        id = id,
        position = exerciseOrder + 1,
        name = exerciseName,
        notes = sanitizeNotes(notes),
        sets = sets.map { it.toRow() },
    )

private fun WorkoutSetEntity.toRow(): WorkoutDetailSetRow =
    WorkoutDetailSetRow(
        id = id,
        setNumber = setNumber,
        weightReps = formatWeightReps(weight, repetitions),
        category = setCategoryLabel(setCategory),
        rpe = formatRpe(rpe),
    )

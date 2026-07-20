package com.myfitnesslog.feature.routine.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.routine.data.RoutineRepository
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDetail
import com.myfitnesslog.feature.routine.ui.RoutineRoutes
import com.myfitnesslog.feature.routine.ui.targetSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the routine detail screen. Combines the routine and its ordered
 * exercises (both Room-backed) into an immutable state. Read-only screen; there
 * are no mutations here.
 */
@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: RoutineRepository,
) : ViewModel() {

    private val routineId: UUID =
        UUID.fromString(checkNotNull(savedStateHandle[RoutineRoutes.ARG_ROUTINE_ID]))

    val uiState: StateFlow<RoutineDetailUiState> =
        combine(
            repository.observeRoutine(routineId),
            repository.observeRoutineExercises(routineId),
        ) { routine, exercises ->
            if (routine == null) {
                RoutineDetailUiState.NotFound
            } else {
                RoutineDetailUiState.Success(
                    name = routine.name,
                    exercises = exercises.map { it.toRow() },
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutineDetailUiState.Loading,
        )
}

private fun RoutineExerciseDetail.toRow(): RoutineExerciseRow =
    RoutineExerciseRow(
        id = routineExercise.id,
        exerciseName = exerciseName,
        targetSummary = routineExercise.targetSummary(),
        notes = routineExercise.notes,
    )

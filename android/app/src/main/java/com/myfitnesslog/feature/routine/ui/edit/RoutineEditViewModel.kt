package com.myfitnesslog.feature.routine.ui.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.routine.data.RoutineRepository
import com.myfitnesslog.feature.routine.data.local.RoutineExerciseDetail
import com.myfitnesslog.feature.routine.ui.RoutineRoutes
import com.myfitnesslog.feature.routine.ui.targetSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the routine edit screen.
 *
 * The name is held locally ([nameState]) so typing does not fight the Room Flow;
 * each change is also persisted via the repository (Room stays the source of
 * truth). The exercise list is observed directly from Room and updates
 * reactively — including after the exercise picker adds a row.
 */
@HiltViewModel
class RoutineEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RoutineRepository,
) : ViewModel() {

    private val routineId: UUID =
        UUID.fromString(checkNotNull(savedStateHandle[RoutineRoutes.ARG_ROUTINE_ID]))

    // null until the initial name is loaded from Room.
    private val nameState = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            nameState.value = repository.observeRoutine(routineId).first()?.name ?: ""
        }
    }

    val uiState: StateFlow<RoutineEditUiState> =
        combine(
            nameState,
            repository.observeRoutineExercises(routineId),
        ) { name, exercises ->
            if (name == null) {
                RoutineEditUiState.Loading
            } else {
                RoutineEditUiState.Success(name, exercises.map { it.toEditRow() })
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutineEditUiState.Loading,
        )

    fun onNameChange(name: String) {
        nameState.value = name
        viewModelScope.launch { repository.renameRoutine(routineId, name) }
    }

    fun removeExercise(routineExerciseId: UUID) {
        viewModelScope.launch { repository.removeExercise(routineExerciseId) }
    }

    fun moveUp(routineExerciseId: UUID) = move(routineExerciseId, offset = -1)

    fun moveDown(routineExerciseId: UUID) = move(routineExerciseId, offset = 1)

    fun updateTargets(
        routineExerciseId: UUID,
        targetSets: Int,
        minTargetReps: Int,
        maxTargetReps: Int,
        targetRestSeconds: Int?,
        notes: String?,
    ) {
        viewModelScope.launch {
            repository.updateExercise(
                routineExerciseId = routineExerciseId,
                targetSets = targetSets,
                minTargetReps = minTargetReps,
                maxTargetReps = maxTargetReps,
                targetRestSeconds = targetRestSeconds,
                notes = notes,
            )
        }
    }

    private fun move(routineExerciseId: UUID, offset: Int) {
        val current = (uiState.value as? RoutineEditUiState.Success)?.exercises ?: return
        val index = current.indexOfFirst { it.id == routineExerciseId }
        val target = index + offset
        if (index == -1 || target !in current.indices) return
        val ids = current.map { it.id }.toMutableList()
        ids[index] = ids[target].also { ids[target] = ids[index] }
        viewModelScope.launch { repository.reorderExercises(routineId, ids) }
    }
}

private fun RoutineExerciseDetail.toEditRow(): RoutineExerciseEditRow =
    RoutineExerciseEditRow(
        id = routineExercise.id,
        exerciseName = exerciseName,
        targetSummary = routineExercise.targetSummary(),
        targetSets = routineExercise.targetSets,
        minTargetReps = routineExercise.minTargetReps,
        maxTargetReps = routineExercise.maxTargetReps,
        targetRestSeconds = routineExercise.targetRestSeconds,
        notes = routineExercise.notes,
    )

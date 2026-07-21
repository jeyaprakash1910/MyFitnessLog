package com.myfitnesslog.feature.routine.ui.picker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.routine.data.RoutineRepository
import com.myfitnesslog.feature.routine.ui.RoutineRoutes
import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.ui.WorkoutRoutes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * Shared exercise picker used by two workflows, selected by which nav argument is
 * present:
 *  - `routineId`         → add the exercise to a routine (routine editing), or
 *  - `workoutSessionId`  → add the exercise to an active manual workout.
 *
 * One implementation, one list; selecting an exercise adds it to the correct
 * target and pops back. Because Room is the source of truth, the originating
 * screen updates reactively with no navigation-result plumbing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExercisePickerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exerciseRepository: ExerciseRepository,
    private val routineRepository: RoutineRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    object Added

    private val routineId: UUID? =
        savedStateHandle.get<String>(RoutineRoutes.ARG_ROUTINE_ID)?.let(UUID::fromString)
    private val workoutSessionId: UUID? =
        savedStateHandle.get<String>(WorkoutRoutes.ARG_SESSION_ID)?.let(UUID::fromString)

    private val query = MutableStateFlow("")

    private val _events = MutableSharedFlow<Added>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private val refreshState = MutableStateFlow<RefreshState>(RefreshState.Idle)

    init {
        // The picker is where exercises are actually needed, so it is where the
        // library is fetched. Nothing else in the app downloads it: the only
        // other caller is the Exercise Library screen, which a user may never
        // open. Without this, a fresh install shows an empty picker forever and
        // no exercise can be added to any routine or workout.
        refreshLibrary()
    }

    val uiState: StateFlow<ExercisePickerUiState> =
        combine(
            query,
            query.flatMapLatest { q ->
                exerciseRepository.observeFiltered(categoryId = null, query = q.trim().ifBlank { null })
            },
            refreshState,
        ) { currentQuery, exercises, refresh ->
            ExercisePickerUiState(
                query = currentQuery,
                // Straight from Room: cached rows appear immediately and are
                // never cleared by a failing refresh.
                exercises = exercises.map { ExercisePickerItem(it.id, it.name) },
                isRefreshing = refresh is RefreshState.Loading,
                errorMessage = (refresh as? RefreshState.Failed)?.message,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExercisePickerUiState(),
        )

    /** Re-attempts the library download after a failure. */
    fun onRetry() = refreshLibrary()

    private fun refreshLibrary() {
        viewModelScope.launch {
            refreshState.value = RefreshState.Loading
            refreshState.value = try {
                exerciseRepository.refreshLibrary()
                RefreshState.Idle
            } catch (throwable: Throwable) {
                // Deliberately swallowed into state rather than rethrown: being
                // offline is normal here, and the cached list stays usable.
                RefreshState.Failed(throwable.message ?: "Could not load exercises.")
            }
        }
    }

    private sealed interface RefreshState {
        data object Idle : RefreshState
        data object Loading : RefreshState
        data class Failed(val message: String) : RefreshState
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onExerciseSelected(exerciseId: UUID) {
        val name = uiState.value.exercises.firstOrNull { it.id == exerciseId }?.name.orEmpty()
        viewModelScope.launch {
            when {
                routineId != null -> routineRepository.addExercise(
                    routineId = routineId,
                    exerciseId = exerciseId,
                    targetSets = DEFAULT_TARGET_SETS,
                    minTargetReps = DEFAULT_MIN_REPS,
                    maxTargetReps = DEFAULT_MAX_REPS,
                    targetRestSeconds = DEFAULT_REST_SECONDS,
                    notes = null,
                )
                workoutSessionId != null -> workoutRepository.addExercise(
                    sessionId = workoutSessionId,
                    exerciseId = exerciseId,
                    exerciseName = name,
                )
            }
            _events.emit(Added)
        }
    }

    private companion object {
        const val DEFAULT_TARGET_SETS = 3
        const val DEFAULT_MIN_REPS = 8
        const val DEFAULT_MAX_REPS = 12
        const val DEFAULT_REST_SECONDS = 90
    }
}

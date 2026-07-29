package com.myfitnesslog.feature.workout.ui.indicator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.routine.data.RoutineRepository
import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.domain.WorkoutClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject

/** Derived, read-only state for the persistent workout indicator (V2 Milestone G). */
sealed interface WorkoutIndicatorUiState {
    /** No workout is active — the indicator is not shown. */
    data object Hidden : WorkoutIndicatorUiState

    /**
     * A workout is active. [routineName] is the source routine's name ("Workout" for a
     * manual/ad-hoc session), [elapsed] is derived from the session's startedAt, and
     * [currentExercise] is the exercise the user is on (or null before any is added).
     */
    data class Visible(
        val elapsed: Duration,
        val routineName: String,
        val currentExercise: String?,
    ) : WorkoutIndicatorUiState
}

/**
 * Exposes the persistent workout indicator's state as a **read-only projection** over
 * the single active session (V2 Workout Persistence Contract). It observes the active
 * session, its routine name, and its exercises/sets, deriving elapsed time and the
 * current exercise. The one mutation it owns is [discardActiveWorkout] — a deliberate,
 * user-confirmed action wired to the indicator's bin icon.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutIndicatorViewModel @Inject constructor(
    private val repository: WorkoutRepository,
    private val routineRepository: RoutineRepository,
    private val clock: Clock,
) : ViewModel() {

    val state: StateFlow<WorkoutIndicatorUiState> =
        repository.observeActiveSession().flatMapLatest { session ->
            if (session == null) {
                flowOf(WorkoutIndicatorUiState.Hidden)
            } else {
                combine(
                    routineNameFlow(session.routineId),
                    currentExerciseFlow(session.id),
                    oneSecondTicker(),
                ) { routineName, currentExercise, _ ->
                    WorkoutIndicatorUiState.Visible(
                        elapsed = WorkoutClock.elapsed(session.startedAt, session.endedAt, clock.instant()),
                        routineName = routineName,
                        currentExercise = currentExercise,
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkoutIndicatorUiState.Hidden,
        )

    /** Discards the active workout (bin icon → confirmation → here). No-op if none active. */
    fun discardActiveWorkout() {
        viewModelScope.launch {
            val session = repository.observeActiveSession().first() ?: return@launch
            runCatching { repository.discardWorkout(session.id) }
        }
    }

    private fun routineNameFlow(routineId: UUID?): Flow<String> =
        if (routineId == null) {
            flowOf(MANUAL_LABEL)
        } else {
            routineRepository.observeRoutine(routineId).map { it?.name ?: MANUAL_LABEL }
        }

    /**
     * The exercise the user is currently on: the first (by order) whose completed set
     * count is still below its target; once all are met, the last exercise. Null when
     * the session has no exercises yet. Derived purely from persisted data.
     */
    private fun currentExerciseFlow(sessionId: UUID): Flow<String?> =
        repository.observeWorkoutExercises(sessionId).flatMapLatest { exercises ->
            if (exercises.isEmpty()) {
                flowOf(null)
            } else {
                combine(
                    exercises.map { exercise ->
                        repository.observeSets(exercise.id).map { sets -> exercise to sets.size }
                    },
                ) { counts ->
                    val ordered = counts.toList()
                    val current = ordered.firstOrNull { (ex, done) -> done < ex.targetSets }
                        ?: ordered.last()
                    current.first.exerciseName
                }
            }
        }

    private fun oneSecondTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    private companion object {
        const val MANUAL_LABEL = "Workout"
    }
}

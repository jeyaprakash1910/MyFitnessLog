package com.myfitnesslog.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import com.myfitnesslog.feature.workout.domain.RestTimer
import com.myfitnesslog.feature.workout.domain.RestTimerState
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import com.myfitnesslog.feature.workout.domain.WorkoutClock
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the active workout screen.
 *
 * On creation it resolves which session to show: if a `routineId` nav argument
 * is present it starts (or resumes, via [StartWorkoutUseCase]) a workout from
 * that routine; otherwise it resumes the currently active session. The state is
 * built reactively from Room (session + exercises + each exercise's sets), so
 * every mutation is reflected automatically.
 *
 * Mutations are delegated to the repository, which enforces completed-workout
 * immutability; the ViewModel wraps them defensively so a rejected write never
 * crashes the UI.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
    private val startWorkout: StartWorkoutUseCase,
    private val clock: Clock,
) : ViewModel() {

    /** One-shot navigation events emitted after the workout ends. */
    enum class Event { COMPLETED, DISCARDED }

    private val routineId: UUID? =
        savedStateHandle.get<String>(WorkoutRoutes.ARG_ROUTINE_ID)?.let(UUID::fromString)

    private data class Resolution(val resolved: Boolean, val sessionId: UUID?)

    private val resolution = MutableStateFlow(Resolution(resolved = false, sessionId = null))

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // --- Timers ---

    private val restTimerController = RestTimer(viewModelScope)

    /** Transient rest countdown state. */
    val restTimer: StateFlow<RestTimerState> = restTimerController.state

    private val sessionFlow: Flow<WorkoutSessionEntity?> = resolution.flatMapLatest { r ->
        if (r.resolved && r.sessionId != null) repository.observeSession(r.sessionId) else flowOf(null)
    }

    /**
     * Workout elapsed time, recomputed each second from the session's startedAt
     * (never stored). Freezes once the session ends because [WorkoutClock.elapsed]
     * uses endedAt when present. It naturally reconstructs after process death
     * because it derives entirely from persisted data.
     */
    val elapsed: StateFlow<Duration> =
        combine(sessionFlow, oneSecondTicker()) { session, _ ->
            if (session == null) {
                Duration.ZERO
            } else {
                WorkoutClock.elapsed(session.startedAt, session.endedAt, clock.instant())
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = Duration.ZERO,
        )

    /**
     * Starts a manual (routine-less) workout, or resumes the active one if a
     * workout is already in progress (single-active invariant). Used from the
     * "No active workout" state on the Workout tab.
     */
    fun startManualWorkout() {
        viewModelScope.launch {
            val sessionId = startWorkout(null)
            resolution.value = Resolution(resolved = true, sessionId = sessionId)
        }
    }

    fun startRest(totalSeconds: Int) = restTimerController.start(totalSeconds)

    fun restartRest() = restTimerController.restart()

    fun cancelRest() = restTimerController.cancel()

    fun skipRest() = restTimerController.skip()

    private fun oneSecondTicker(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    init {
        viewModelScope.launch {
            val sessionId = if (routineId != null) {
                startWorkout(routineId)
            } else {
                repository.observeActiveSession().first()?.id
            }
            resolution.value = Resolution(resolved = true, sessionId = sessionId)
        }
    }

    val uiState: StateFlow<WorkoutUiState> =
        resolution.flatMapLatest { r ->
            when {
                !r.resolved -> flowOf(WorkoutUiState.Loading)
                r.sessionId == null -> flowOf(WorkoutUiState.NoActiveWorkout)
                else -> combine(
                    repository.observeSession(r.sessionId),
                    exercisesWithSets(r.sessionId),
                ) { session, exercises ->
                    if (session == null) {
                        WorkoutUiState.NoActiveWorkout
                    } else {
                        WorkoutUiState.Active(
                            sessionId = session.id,
                            exercises = exercises.map { (exercise, sets) -> exercise.toUi(sets) },
                            isReadOnly = session.status != WorkoutStatus.IN_PROGRESS,
                        )
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkoutUiState.Loading,
        )

    fun addSet(
        workoutExerciseId: UUID,
        weight: BigDecimal,
        repetitions: Int,
        setCategory: SetCategory,
        rpe: BigDecimal?,
    ) = launchCatching {
        repository.addSet(workoutExerciseId, weight, repetitions, setCategory, rpe)
    }

    fun updateSet(
        setId: UUID,
        weight: BigDecimal,
        repetitions: Int,
        setCategory: SetCategory,
        rpe: BigDecimal?,
        rir: BigDecimal?,
        isCompleted: Boolean,
    ) = launchCatching {
        repository.updateSet(setId, weight, repetitions, setCategory, rpe, rir, isCompleted)
    }

    fun toggleCompletion(setId: UUID) {
        val set = activeExercises()?.flatMap { it.sets }?.firstOrNull { it.id == setId } ?: return
        updateSet(setId, set.weight, set.repetitions, set.setCategory, set.rpe, set.rir, !set.isCompleted)
    }

    fun deleteSet(setId: UUID) = launchCatching { repository.deleteSet(setId) }

    fun completeWorkout() = finish(Event.COMPLETED)

    fun discardWorkout() = finish(Event.DISCARDED)

    private fun finish(event: Event) {
        val sessionId = resolution.value.sessionId ?: return
        viewModelScope.launch {
            runCatching {
                if (event == Event.COMPLETED) repository.completeWorkout(sessionId)
                else repository.discardWorkout(sessionId)
            }
            // A finished workout stops the rest timer; the elapsed timer freezes
            // automatically via the session's endedAt.
            restTimerController.cancel()
            _events.emit(event)
        }
    }

    private fun activeExercises(): List<WorkoutExerciseUi>? =
        (uiState.value as? WorkoutUiState.Active)?.exercises

    private fun exercisesWithSets(sessionId: UUID) =
        repository.observeWorkoutExercises(sessionId).flatMapLatest { exercises ->
            if (exercises.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    exercises.map { exercise ->
                        repository.observeSets(exercise.id).map { sets -> exercise to sets }
                    },
                ) { it.toList() }
            }
        }

    private fun launchCatching(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}

private fun WorkoutExerciseEntity.toUi(sets: List<WorkoutSetEntity>): WorkoutExerciseUi =
    WorkoutExerciseUi(
        id = id,
        exerciseName = exerciseName,
        targetSummary = targetSummary(),
        sets = sets.map { it.toUi() },
    )

private fun WorkoutSetEntity.toUi(): WorkoutSetUi =
    WorkoutSetUi(id, setNumber, weight, repetitions, setCategory, rpe, rir, isCompleted)

private fun WorkoutExerciseEntity.targetSummary(): String {
    val reps = if (minTargetReps == maxTargetReps) "$minTargetReps reps" else "$minTargetReps–$maxTargetReps reps"
    val parts = mutableListOf("$targetSets sets", reps)
    targetRestSeconds?.let { parts.add("rest ${it}s") }
    return parts.joinToString(" · ")
}

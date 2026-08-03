package com.myfitnesslog.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepository
import com.myfitnesslog.feature.history.data.local.PreviousSetPerformance
import com.myfitnesslog.feature.settings.data.SettingsRepository
import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues
import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import com.myfitnesslog.feature.workout.domain.RestTimer
import com.myfitnesslog.feature.workout.domain.RestTimerState
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import com.myfitnesslog.feature.workout.domain.WorkoutClock
import com.myfitnesslog.feature.workout.domain.logging.LoggedSetRow
import com.myfitnesslog.feature.workout.domain.logging.RestEffect
import com.myfitnesslog.feature.workout.domain.logging.SetInput
import com.myfitnesslog.feature.workout.domain.logging.SetMutation
import com.myfitnesslog.feature.workout.domain.logging.SetTransitions
import com.myfitnesslog.feature.workout.domain.logging.WorkoutRowMerger
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
import java.time.Clock
import java.time.Duration
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the active workout screen (V2 Milestone B).
 *
 * It is strictly an **orchestration layer** over the Milestone A domain state
 * machine ([SetTransitions]). It merges persisted completed sets (from Room) with
 * transient planned rows (held here in [drafts]), turns user intents into
 * [SetTransitions] calls, dispatches the resulting [SetMutation] to the existing
 * repository methods, and reflects the result in [WorkoutUiState]. It contains no
 * completion/edit/undo/delete rules of its own (those live in the domain), and it
 * never touches Room or sync directly.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: WorkoutRepository,
    private val historyRepository: WorkoutHistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val startWorkout: StartWorkoutUseCase,
    private val clock: Clock,
    private val restTimerController: RestTimer,
) : ViewModel() {

    /** One-shot navigation events emitted after the workout ends. */
    enum class Event { COMPLETED, DISCARDED }

    private val routineId: UUID? =
        savedStateHandle.get<String>(WorkoutRoutes.ARG_ROUTINE_ID)?.let(UUID::fromString)

    private data class Resolution(val resolved: Boolean, val sessionId: UUID?)

    private val resolution = MutableStateFlow(Resolution(resolved = false, sessionId = null))

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    // --- Transient planned-row state (never persisted; INV-4) --------------

    private data class DraftRow(val key: UUID, val input: SetInput, val order: Int)

    /** Per-exercise transient rows: intentions the user has not yet completed. */
    private val drafts = MutableStateFlow<Map<UUID, List<DraftRow>>>(emptyMap())

    /**
     * Per-exercise high-water mark for the ordering slot handed to new planned rows
     * ([DraftRow.order]). Kept above every completed set number and every live draft
     * so an appended row always sorts to the bottom (see [WorkoutRowMerger]).
     */
    private val nextSlot = mutableMapOf<UUID, Int>()

    /** Exercises already seeded with their planned rows (seed once). */
    private val seeded = mutableSetOf<UUID>()

    /** Previous-workout performance per master exerciseId (read-only projection, §6). */
    private val previousByExercise = MutableStateFlow<Map<UUID, List<PreviousSetPerformance>>>(emptyMap())

    // --- Timers ---

    /** Transient rest countdown state (app-scoped controller; see WorkoutModule). */
    val restTimer: StateFlow<RestTimerState> = restTimerController.state

    private val sessionFlow: Flow<WorkoutSessionEntity?> = resolution.flatMapLatest { r ->
        if (r.resolved && r.sessionId != null) repository.observeSession(r.sessionId) else flowOf(null)
    }

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
        // Seed each exercise's planned rows once, from its target set count minus any
        // already-persisted sets (so a resumed workout is not over-populated).
        viewModelScope.launch {
            resolution.flatMapLatest { r ->
                if (r.resolved && r.sessionId != null) exercisesWithSets(r.sessionId) else flowOf(emptyList())
            }.collect { list ->
                // The PREVIOUS lookup strategy and the current session's routine are
                // resolved once per emission; both are needed to pick the read below.
                val strategy = settingsRepository.previousWorkoutValues.first()
                val currentRoutineId = resolution.value.sessionId
                    ?.let { repository.observeSession(it).first()?.routineId }
                list.forEach { (exercise, sets) ->
                    if (seeded.add(exercise.id)) {
                        val plannedCount = (exercise.targetSets - sets.size).coerceAtLeast(0)
                        // Slots continue after any already-persisted set numbers so seeded
                        // planned rows render below completed ones.
                        val startSlot = sets.maxOfOrNull { it.setNumber } ?: 0
                        val rows = List(plannedCount) { i ->
                            DraftRow(UUID.randomUUID(), SetInput.EMPTY, order = startSlot + i + 1)
                        }
                        nextSlot[exercise.id] = startSlot + plannedCount
                        drafts.update { it + (exercise.id to rows) }
                        // Read-only previous-performance projection for this exercise,
                        // sourced per the user's "Previous Workout Values" setting.
                        val previous = when (strategy) {
                            PreviousWorkoutValues.ANY_WORKOUT ->
                                historyRepository.getPreviousSets(exercise.exerciseId)
                            PreviousWorkoutValues.SAME_ROUTINE ->
                                historyRepository.getPreviousSetsInRoutine(exercise.exerciseId, currentRoutineId)
                        }
                        previousByExercise.update { it + (exercise.exerciseId to previous) }
                    }
                }
            }
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
                    drafts,
                    previousByExercise,
                ) { session, exercises, draftMap, previousMap ->
                    if (session == null) {
                        WorkoutUiState.NoActiveWorkout
                    } else {
                        val readOnly = session.status != WorkoutStatus.IN_PROGRESS
                        WorkoutUiState.Active(
                            sessionId = session.id,
                            exercises = exercises.map { (exercise, sets) ->
                                exercise.toUi(
                                    sets,
                                    draftMap[exercise.id].orEmpty(),
                                    previousMap[exercise.exerciseId].orEmpty(),
                                    readOnly,
                                )
                            },
                            isReadOnly = readOnly,
                        )
                    }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkoutUiState.Loading,
        )

    // --- Intents ------------------------------------------------------------

    /** Add an empty planned row to an exercise (transient — persists nothing). */
    fun onAddSet(exerciseId: UUID) {
        val slot = nextSlotFor(exerciseId)
        drafts.update { m -> m + (exerciseId to (m[exerciseId].orEmpty() + DraftRow(UUID.randomUUID(), SetInput.EMPTY, slot))) }
    }

    /** Allocates the next ordering slot for a new planned row (strictly below existing rows). */
    private fun nextSlotFor(exerciseId: UUID): Int {
        val current = maxOf(nextSlot[exerciseId] ?: 0, drafts.value[exerciseId].orEmpty().maxOfOrNull { it.order } ?: 0)
        val next = current + 1
        nextSlot[exerciseId] = next
        return next
    }

    /**
     * Commit inline field text (focus-loss / IME Done). For a persisted row this is
     * an edit ([SetTransitions.edit] → `updateSet`); for a transient row it stores
     * the typed values in the draft (no persistence).
     */
    fun onCommitRow(exerciseId: UUID, rowKey: String, weightText: String, repsText: String) {
        val weight = weightText.trim().toBigDecimalOrNull()
        val reps = repsText.trim().toIntOrNull()
        val draftKey = rowKey.draftKeyOrNull()
        if (draftKey != null) {
            drafts.update { m ->
                m + (exerciseId to m[exerciseId].orEmpty().map {
                    if (it.key == draftKey) it.copy(input = it.input.copy(weight = weight, repetitions = reps)) else it
                })
            }
            return
        }
        val setId = rowKey.setIdOrNull() ?: return
        val row = row(exerciseId, rowKey) ?: return
        if (weight == null || reps == null) return // never clear a completed set to empty
        val result = SetTransitions.edit(
            LoggedSetRow(row.setNumber, row.toInput(), setId),
            row.toInput().copy(weight = weight, repetitions = reps),
        )
        dispatch(exerciseId, result.mutation, draftKey = null)
    }

    /** Toggle completion: complete a transient row, or undo a completed one. */
    fun onToggleComplete(exerciseId: UUID, rowKey: String, weightText: String, repsText: String) {
        val draftKey = rowKey.draftKeyOrNull()
        if (draftKey != null) {
            // Preserve the draft's already-entered RPE and set category; the checkbox
            // only carries the inline weight/reps text, so rebuilding SetInput from
            // those alone would drop an RPE the row already holds (e.g. one restored
            // by a prior undo).
            val existing = row(exerciseId, rowKey)?.toInput() ?: SetInput.EMPTY
            val input = existing.copy(
                weight = weightText.trim().toBigDecimalOrNull(),
                repetitions = repsText.trim().toIntOrNull(),
            )
            val result = SetTransitions.complete(LoggedSetRow(0, input, persistedId = null), input)
            dispatch(exerciseId, result.mutation, draftKey = draftKey)
            applyRest(result.rest, exerciseId)
            return
        }
        val setId = rowKey.setIdOrNull() ?: return
        val row = row(exerciseId, rowKey) ?: return
        val result = SetTransitions.undo(LoggedSetRow(row.setNumber, row.toInput(), setId))
        // Restore the reverted set into its original slot so it stays in place (not last).
        dispatch(exerciseId, result.mutation, draftKey = null, restoreInput = result.row.input, restoreOrder = row.slot)
        applyRest(result.rest, exerciseId)
    }

    /**
     * Apply an RPE selection from the bottom sheet's Done (Milestone C). This is the
     * *same* completion/edit transition as [onToggleComplete]/[onCommitRow] — it only
     * supplies the `rpe` value. Weight/reps come from the current (committed) row, so
     * completing a planned row still requires them (an empty row stays planned).
     */
    fun onRpeSelected(exerciseId: UUID, rowKey: String, rpe: java.math.BigDecimal?) {
        val row = row(exerciseId, rowKey) ?: return
        val input = SetInput(row.weight, row.repetitions, rpe, row.setCategory)
        val draftKey = rowKey.draftKeyOrNull()
        if (draftKey != null) {
            val result = SetTransitions.complete(LoggedSetRow(row.setNumber, input, persistedId = null), input)
            dispatch(exerciseId, result.mutation, draftKey = draftKey)
            applyRest(result.rest, exerciseId)
            return
        }
        val setId = rowKey.setIdOrNull() ?: return
        val result = SetTransitions.edit(LoggedSetRow(row.setNumber, row.toInput(), setId), input)
        dispatch(exerciseId, result.mutation, draftKey = null)
        applyRest(result.rest, exerciseId)
    }

    /**
     * Consumes a [RestEffect] from a transition (Milestone D). The timer is a pure
     * *consumer* of the domain event — it decides nothing about completion. START uses
     * the completing exercise's rest duration; STOP cancels; NONE does nothing.
     */
    private fun applyRest(effect: RestEffect, exerciseId: UUID) {
        when (effect) {
            RestEffect.START -> restTimerController.start(restSecondsOf(exerciseId))
            RestEffect.STOP -> restTimerController.cancel()
            RestEffect.NONE -> Unit
        }
    }

    private fun restSecondsOf(exerciseId: UUID): Int =
        activeExercise(exerciseId)?.restSeconds ?: DEFAULT_REST_SECONDS

    /** Adjust the running rest countdown by ±seconds (docked bar). */
    fun adjustRest(deltaSeconds: Int) = restTimerController.adjust(deltaSeconds)

    // --- Exercise management (session-scoped; Milestone F) ------------------

    /** Set the per-exercise rest duration (spec §5); negative values are clamped to 0. */
    fun onSetExerciseRest(exerciseId: UUID, restSeconds: Int) =
        launchCatching { repository.updateExerciseRest(exerciseId, restSeconds.coerceAtLeast(0)) }

    fun onMoveExerciseUp(exerciseId: UUID) = launchCatching { repository.moveExercise(exerciseId, up = true) }
    fun onMoveExerciseDown(exerciseId: UUID) = launchCatching { repository.moveExercise(exerciseId, up = false) }
    fun onRemoveExercise(exerciseId: UUID) = launchCatching { repository.removeExercise(exerciseId) }

    /** Delete a row: drop a transient draft, or tombstone a persisted set. */
    fun onDeleteRow(exerciseId: UUID, rowKey: String) {
        val draftKey = rowKey.draftKeyOrNull()
        if (draftKey != null) {
            val result = SetTransitions.delete(LoggedSetRow(0, SetInput.EMPTY, persistedId = null))
            dispatch(exerciseId, result.mutation, draftKey = draftKey)
            return
        }
        val setId = rowKey.setIdOrNull() ?: return
        val row = row(exerciseId, rowKey) ?: return
        val result = SetTransitions.delete(LoggedSetRow(row.setNumber, row.toInput(), setId))
        dispatch(exerciseId, result.mutation, draftKey = null)
    }

    /**
     * Applies a [SetMutation] to the repository / draft state. This is the single
     * dispatch point; the *decision* of which mutation to apply was made by
     * [SetTransitions], not here.
     */
    private fun dispatch(
        exerciseId: UUID,
        mutation: SetMutation,
        draftKey: UUID?,
        restoreInput: SetInput? = null,
        restoreOrder: Int? = null,
    ) {
        when (mutation) {
            is SetMutation.Insert -> launchCatching {
                repository.addSet(exerciseId, mutation.input.weight!!, mutation.input.repetitions!!, mutation.input.setCategory, mutation.input.rpe)
                if (draftKey != null) removeDraft(exerciseId, draftKey)
            }
            is SetMutation.Update -> launchCatching {
                val row = rowById(exerciseId, mutation.id) ?: return@launchCatching
                repository.updateSet(mutation.id, mutation.input.weight!!, mutation.input.repetitions!!, mutation.input.setCategory, mutation.input.rpe, row.rir, isCompleted = true)
            }
            is SetMutation.DeleteWithTombstone -> launchCatching {
                repository.deleteSet(mutation.id)
                if (restoreInput != null) {
                    val order = restoreOrder ?: nextSlotFor(exerciseId)
                    drafts.update { m -> m + (exerciseId to (m[exerciseId].orEmpty() + DraftRow(UUID.randomUUID(), restoreInput, order))) }
                }
            }
            SetMutation.RemoveTransientRow -> if (draftKey != null) removeDraft(exerciseId, draftKey)
            SetMutation.None -> Unit
        }
    }

    private fun removeDraft(exerciseId: UUID, draftKey: UUID) {
        drafts.update { m -> m + (exerciseId to m[exerciseId].orEmpty().filterNot { it.key == draftKey }) }
    }

    fun completeWorkout() = finish(Event.COMPLETED)
    fun discardWorkout() = finish(Event.DISCARDED)

    private fun finish(event: Event) {
        val sessionId = resolution.value.sessionId ?: return
        viewModelScope.launch {
            runCatching {
                if (event == Event.COMPLETED) repository.completeWorkout(sessionId)
                else repository.discardWorkout(sessionId)
            }
            restTimerController.cancel()
            _events.emit(event)
        }
    }

    // --- helpers ------------------------------------------------------------

    private fun activeExercise(exerciseId: UUID): WorkoutExerciseUi? =
        (uiState.value as? WorkoutUiState.Active)?.exercises?.firstOrNull { it.id == exerciseId }

    private fun row(exerciseId: UUID, rowKey: String): WorkoutSetRowUi? =
        activeExercise(exerciseId)?.rows?.firstOrNull { it.rowKey == rowKey }

    private fun rowById(exerciseId: UUID, setId: UUID): WorkoutSetRowUi? =
        activeExercise(exerciseId)?.rows?.firstOrNull { it.rowKey == setKey(setId) }

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

    private fun WorkoutExerciseEntity.toUi(
        sets: List<WorkoutSetEntity>,
        draftRows: List<DraftRow>,
        previous: List<PreviousSetPerformance>,
        readOnly: Boolean,
    ): WorkoutExerciseUi {
        val completedSorted = sets.sortedBy { it.setNumber }
        // Each row carries its stable ordering *slot* in setNumber (completed → persisted
        // set number; planned → assigned order); the merger interleaves by slot then
        // renumbers 1..n. This keeps an undone set in place (see [WorkoutRowMerger]).
        val completedDomain = completedSorted.map { LoggedSetRow(it.setNumber, it.toInput(), it.id) }
        val plannedDomain = if (readOnly) emptyList() else draftRows.map { LoggedSetRow(it.order, it.input, persistedId = null) }
        val merged = WorkoutRowMerger.merge(completedDomain, plannedDomain)

        // Parallel metadata, built in the same (completed ++ planned) order and sorted by
        // the same slot with the same stable sort, so keys/rir/slot (not carried by the
        // domain row) stay aligned with [merged] after the interleave.
        val meta = (completedSorted.map { RowMeta(it.setNumber, setKey(it.id), it.rir) } +
            (if (readOnly) emptyList() else draftRows.map { RowMeta(it.order, draftKey(it.key), null) }))
            .sortedBy { it.slot }

        // Previous performance matched strictly by set number (INV-12); missing → null (→ "-").
        val previousByNumber = previous.associateBy { it.setNumber }

        val rows = merged.mapIndexed { index, r ->
            val m = meta[index]
            WorkoutSetRowUi(
                rowKey = m.key,
                setNumber = r.setNumber,
                slot = m.slot,
                weight = r.input.weight,
                repetitions = r.input.repetitions,
                rpe = r.input.rpe,
                rir = m.rir,
                setCategory = r.input.setCategory,
                isCompleted = r.persistedId != null,
                previous = previousByNumber[r.setNumber]?.formatPrevious(),
            )
        }
        return WorkoutExerciseUi(id, exerciseName, targetSummary(), targetRestSeconds ?: DEFAULT_REST_SECONDS, rows)
    }

    private fun WorkoutExerciseEntity.targetSummary(): String {
        val reps = if (minTargetReps == maxTargetReps) "$minTargetReps reps" else "$minTargetReps–$maxTargetReps reps"
        val parts = mutableListOf("$targetSets sets", reps)
        targetRestSeconds?.let { parts.add("rest ${it}s") }
        return parts.joinToString(" · ")
    }

    /** Row metadata carried alongside the domain rows through the slot-ordered merge. */
    private data class RowMeta(val slot: Int, val key: String, val rir: java.math.BigDecimal?)

    private companion object {
        const val SET_PREFIX = "set:"
        const val DRAFT_PREFIX = "draft:"
        const val DEFAULT_REST_SECONDS = 90
    }

    private fun setKey(id: UUID) = "$SET_PREFIX$id"
    private fun draftKey(key: UUID) = "$DRAFT_PREFIX$key"
    private fun String.setIdOrNull(): UUID? =
        if (startsWith(SET_PREFIX)) UUID.fromString(removePrefix(SET_PREFIX)) else null
    private fun String.draftKeyOrNull(): UUID? =
        if (startsWith(DRAFT_PREFIX)) UUID.fromString(removePrefix(DRAFT_PREFIX)) else null
}

private fun WorkoutSetEntity.toInput() = SetInput(weight, repetitions, rpe, setCategory)

/** "32kg × 20 @ 7 rpe" (rpe omitted when absent) — the PREVIOUS cell text (spec §6). */
private fun PreviousSetPerformance.formatPrevious(): String {
    val base = "${weight.toPlainString()}kg × $repetitions"
    return if (rpe != null) "$base @ ${rpe.toPlainString()} rpe" else base
}

private fun WorkoutSetRowUi.toInput() = SetInput(weight, repetitions, rpe, setCategory)

private fun <T> MutableStateFlow<T>.update(transform: (T) -> T) {
    while (true) {
        val prev = value
        val next = transform(prev)
        if (compareAndSet(prev, next)) return
    }
}

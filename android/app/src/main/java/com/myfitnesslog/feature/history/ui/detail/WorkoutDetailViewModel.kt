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
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.feature.workout.data.SetWriteIntent
import com.myfitnesslog.feature.workout.data.WorkoutRepository
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the workout detail screen.
 *
 * Reads through the read-only [WorkoutHistoryRepository] (ADR-0011), combining a
 * completed session, its snapshotted exercises and every performed set into an
 * immutable state, grouping sets under their exercise while preserving the
 * captured exercise and set order. Only COMPLETED workouts resolve; anything else
 * maps to [WorkoutDetailUiState.NotFound].
 *
 * ## Corrections (ADR-0018)
 *
 * The screen was read-only until 2026-08-09. It now offers the three corrections
 * ADR-0018 permits, all of them statements about what the user actually did:
 *
 *  * correcting the weight, reps and RPE of a performed set,
 *  * adding a set that was performed but never logged,
 *  * deleting a set that was logged but not performed.
 *
 * That is the "dedicated workout edit flow" ADR-0004 reserved and never built, and
 * it exists because a logged set can simply be wrong. The backend and
 * [WorkoutRepository] have allowed all three on a COMPLETED session since
 * 2026-08-08; until 2026-08-12 only the first was reachable from the phone.
 *
 * Two boundaries are deliberate and the UI has to make them visible rather than
 * failing after the fact:
 *
 *  * **Only what was performed is editable.** The planning snapshot, meaning
 *    exercise name, order and targets, stays locked, because it records what the
 *    plan was on the day. Rewriting it is the failure ADR-0004 exists to prevent.
 *  * **The write goes through [WorkoutRepository], not the history repository.**
 *    History stays a read-only projection. Routing a correction through the
 *    logging repository also means it gets the outbox for free: the row is marked
 *    pending and a sync is requested, and Stage 2's refresh leaves pending rows
 *    alone, so a correction cannot be overwritten by the backend before it
 *    uploads.
 */
@HiltViewModel
class WorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: WorkoutHistoryRepository,
    private val workoutRepository: WorkoutRepository,
) : ViewModel() {

    private val sessionId: UUID =
        UUID.fromString(checkNotNull(savedStateHandle[WorkoutHistoryRoutes.ARG_SESSION_ID]))

    private val correction = MutableStateFlow<SetCorrection?>(null)
    private val confirmingDiscard = MutableStateFlow(false)

    /**
     * Turns true once the workout has been discarded, so the screen can leave.
     *
     * A StateFlow rather than a one-shot event on purpose. A `SharedFlow` with no
     * replay drops the value if the collector has not started yet, which is exactly
     * the bug TD-015 recorded as its third cause. A latched flag cannot be missed:
     * a collector arriving late still observes it.
     *
     * The screen cannot infer this from [WorkoutDetailUiState.NotFound], because
     * that state also means "this id was never a completed workout" and navigating
     * away on it would fire for reasons that have nothing to do with discarding.
     */
    private val _discarded = MutableStateFlow(false)
    val discarded: StateFlow<Boolean> = _discarded

    val uiState: StateFlow<WorkoutDetailUiState> =
        combine(
            repository.observeWorkout(sessionId),
            repository.observeExercises(sessionId),
            repository.observeSets(sessionId),
            correction,
            confirmingDiscard,
        ) { session, exercises, sets, openCorrection, discardAsked ->
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
                    correction = openCorrection,
                    confirmingDiscard = discardAsked,
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkoutDetailUiState.Loading,
        )

    /**
     * Opens the correction dialog for a set, pre-filled with what was recorded.
     *
     * Reads the row from the state that is already on screen rather than
     * re-querying, because that is exactly what the user is looking at and is what
     * they expect to see in the fields.
     */
    fun onCorrectSet(setId: UUID) {
        val state = uiState.value as? WorkoutDetailUiState.Success ?: return
        val exercise = state.exercises.firstOrNull { ex -> ex.sets.any { it.id == setId } } ?: return
        val row = exercise.sets.first { it.id == setId }
        correction.value = SetCorrection(
            workoutExerciseId = exercise.id,
            setId = row.id,
            setNumber = row.setNumber,
            exerciseName = exercise.name,
            weight = row.weight.stripTrailingZeros().toPlainString(),
            repetitions = row.repetitions.toString(),
            rpe = row.rpeValue?.stripTrailingZeros()?.toPlainString().orEmpty(),
        )
    }

    /**
     * Opens the dialog to add a set that was performed but never logged.
     *
     * The fields start empty rather than pre-filled from the last set. A guessed
     * value that happens to be plausible is the one a user accepts without reading,
     * and this screen exists to make history more accurate, not less.
     */
    fun onAddSet(workoutExerciseId: UUID) {
        val state = uiState.value as? WorkoutDetailUiState.Success ?: return
        val exercise = state.exercises.firstOrNull { it.id == workoutExerciseId } ?: return
        correction.value = SetCorrection(
            workoutExerciseId = exercise.id,
            setId = null,
            // The next position as displayed, so the heading names the set the
            // user is about to see appear. The stored number the repository
            // assigns may be higher if a set was deleted, which is not something
            // the dialog should surface.
            setNumber = exercise.sets.size + 1,
            exerciseName = exercise.name,
            weight = "",
            repetitions = "",
            rpe = "",
        )
    }

    fun onCorrectionWeightChange(text: String) = editCorrection { it.copy(weight = text, error = null) }

    fun onCorrectionRepsChange(text: String) = editCorrection { it.copy(repetitions = text, error = null) }

    fun onCorrectionRpeChange(text: String) = editCorrection { it.copy(rpe = text, error = null) }

    fun onCorrectionDismissed() {
        correction.value = null
    }

    /**
     * Validates and applies the correction.
     *
     * The dialog stays open with a message when the input is unusable, rather than
     * closing and silently discarding the edit. A set that cannot be parsed is the
     * user mid-thought, not an error to swallow.
     *
     * RPE is optional: an empty field clears it, which is how a mistakenly entered
     * effort score is removed.
     */
    fun onCorrectionSaved() {
        val pending = correction.value ?: return
        val weight = pending.weight.trim().toBigDecimalOrNull()
        val reps = pending.repetitions.trim().toIntOrNull()
        val rpeText = pending.rpe.trim()
        val rpe = if (rpeText.isEmpty()) null else rpeText.toBigDecimalOrNull()

        val message = when {
            weight == null || weight.signum() < 0 -> "Enter a weight of 0 or more."
            reps == null || reps <= 0 -> "Enter a rep count of 1 or more."
            rpeText.isNotEmpty() && (rpe == null || rpe < BigDecimal.ONE || rpe > BigDecimal.TEN) ->
                "RPE must be between 1 and 10, or empty."
            else -> null
        }
        if (message != null) {
            correction.value = pending.copy(error = message)
            return
        }

        val setId = pending.setId
        if (setId == null) {
            correction.value = null
            viewModelScope.launch {
                workoutRepository.addSet(
                    workoutExerciseId = pending.workoutExerciseId,
                    weight = weight!!,
                    repetitions = reps!!,
                    // A set added as a correction is a working set with no RIR.
                    // The dialog does not offer either, for the same reason it
                    // does not offer them when editing: they are rarely wrong and
                    // a third and fourth field would bury the two that are.
                    setCategory = SetCategory.WORKING,
                    rpe = rpe,
                    rir = null,
                    intent = SetWriteIntent.CORRECTION,
                )
            }
            return
        }

        val current = (uiState.value as? WorkoutDetailUiState.Success)
            ?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == setId }
            ?: return
        correction.value = null

        viewModelScope.launch {
            workoutRepository.updateSet(
                setId = setId,
                weight = weight!!,
                repetitions = reps!!,
                setCategory = current.setCategory,
                rpe = rpe,
                rir = current.rir,
                // A corrected set stays completed. Corrections change what was
                // performed, never whether it happened.
                isCompleted = true,
                intent = SetWriteIntent.CORRECTION,
            )
        }
    }

    /**
     * Asks for confirmation before deleting. Deleting a set is the one correction
     * that destroys a record rather than changing it, and the row is gone from
     * history the moment it happens, so it does not happen on a single tap.
     */
    fun onDeleteRequested() = editCorrection { it.copy(confirmingDelete = true, error = null) }

    fun onDeleteCancelled() = editCorrection { it.copy(confirmingDelete = false) }

    /**
     * Deletes the set, and its row on the backend with it.
     *
     * [WorkoutRepository.deleteSet] writes an ADR-0007 tombstone in the same
     * transaction as the delete, so this propagates rather than leaving the
     * backend holding a set the phone no longer shows.
     */
    fun onDeleteConfirmed() {
        val setId = correction.value?.setId ?: return
        correction.value = null
        viewModelScope.launch {
            workoutRepository.deleteSet(setId, intent = SetWriteIntent.CORRECTION)
        }
    }

    // --- Discarding the whole workout ---------------------------------------

    /**
     * Asks before removing the workout from history.
     *
     * Always confirmed, and never undoable in the app. The row keeps its exercises
     * and sets on the backend, so the data is recoverable by completing the session
     * again through the API, but nothing here offers that: an undo path for a rare,
     * deliberate, already-confirmed action is a screen nobody would find twice.
     */
    fun onDiscardRequested() {
        confirmingDiscard.value = true
    }

    fun onDiscardCancelled() {
        confirmingDiscard.value = false
    }

    /**
     * Discards the workout, which removes it from history everywhere.
     *
     * The session becomes DISCARDED and is queued for upload. History is COMPLETED
     * only on both clients and on the backend, so it leaves the phone immediately,
     * the web client on its next load, and any other device on its next refresh -
     * where the local row is deleted outright once the backend stops listing it.
     *
     * Nothing is destroyed. DISCARDED is a status, not a deletion.
     */
    fun onDiscardConfirmed() {
        confirmingDiscard.value = false
        viewModelScope.launch {
            workoutRepository.discardWorkout(sessionId)
            // Only after the write, so the screen never leaves on an action that
            // failed and left the workout in place.
            _discarded.value = true
        }
    }

    private fun editCorrection(transform: (SetCorrection) -> SetCorrection) {
        correction.value = correction.value?.let(transform)
    }
}

/**
 * Sets are numbered for display by their position, not by the persisted
 * `setNumber`, so a deletion cannot leave history reading "Set 1, Set 3".
 *
 * This is the same rule the in-progress logging screen already applies in
 * `WorkoutRowMerger`, and applying it here is what makes the two screens agree.
 * The stored number stays untouched on purpose: during logging it is the *slot*
 * that lets an undone set fall back into its original position instead of jumping
 * to the end, and rewriting it would break that. It is also a stable identifier
 * the backend shares, so resequencing rows the user never edited would dirty them
 * for upload and bump their timestamps for a purely cosmetic change.
 */
private fun WorkoutExerciseEntity.toRow(sets: List<WorkoutSetEntity>): WorkoutDetailExerciseRow =
    WorkoutDetailExerciseRow(
        id = id,
        position = exerciseOrder + 1,
        name = exerciseName,
        notes = sanitizeNotes(notes),
        sets = sets.mapIndexed { index, set -> set.toRow(displayNumber = index + 1) },
    )

private fun WorkoutSetEntity.toRow(displayNumber: Int): WorkoutDetailSetRow =
    WorkoutDetailSetRow(
        id = id,
        setNumber = displayNumber,
        weightReps = formatWeightReps(weight, repetitions),
        category = setCategoryLabel(setCategory),
        rpe = formatRpe(rpe),
        weight = weight,
        repetitions = repetitions,
        rpeValue = rpe,
        setCategory = setCategory,
        rir = rir,
    )

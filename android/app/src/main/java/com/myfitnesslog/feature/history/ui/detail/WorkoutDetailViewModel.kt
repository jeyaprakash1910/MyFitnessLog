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
 * The screen was read-only until 2026-08-09. It now offers one write: correcting
 * the weight, reps and RPE of a performed set. That is the "dedicated workout edit
 * flow" ADR-0004 reserved and never built, and it exists because a logged set can
 * simply be wrong.
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

    val uiState: StateFlow<WorkoutDetailUiState> =
        combine(
            repository.observeWorkout(sessionId),
            repository.observeExercises(sessionId),
            repository.observeSets(sessionId),
            correction,
        ) { session, exercises, sets, openCorrection ->
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
            setId = row.id,
            setNumber = row.setNumber,
            exerciseName = exercise.name,
            weight = row.weight.stripTrailingZeros().toPlainString(),
            repetitions = row.repetitions.toString(),
            rpe = row.rpeValue?.stripTrailingZeros()?.toPlainString().orEmpty(),
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

        val current = (uiState.value as? WorkoutDetailUiState.Success)
            ?.exercises?.flatMap { it.sets }?.firstOrNull { it.id == pending.setId }
            ?: return
        correction.value = null

        viewModelScope.launch {
            workoutRepository.updateSet(
                setId = pending.setId,
                weight = weight!!,
                repetitions = reps!!,
                setCategory = current.setCategory,
                rpe = rpe,
                rir = current.rir,
                // A corrected set stays completed. Corrections change what was
                // performed, never whether it happened.
                isCompleted = true,
            )
        }
    }

    private fun editCorrection(transform: (SetCorrection) -> SetCorrection) {
        correction.value = correction.value?.let(transform)
    }
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
        weight = weight,
        repetitions = repetitions,
        rpeValue = rpe,
        setCategory = setCategory,
        rir = rir,
    )

package com.myfitnesslog.feature.history.ui.detail

import com.myfitnesslog.core.data.local.SetCategory
import java.math.BigDecimal
import java.util.UUID

/**
 * Presentation model for one performed set on the detail screen.
 *
 * Carries the raw [weight], [repetitions] and [rpe] alongside their formatted
 * text because the correction dialog has to pre-fill the fields with what was
 * recorded. Parsing them back out of [weightReps] would be a formatter working in
 * reverse, which breaks the first time the format changes.
 */
data class WorkoutDetailSetRow(
    val id: UUID,
    val setNumber: Int,
    val weightReps: String,
    val category: String,
    val rpe: String?,
    val weight: BigDecimal,
    val repetitions: Int,
    val rpeValue: BigDecimal?,
    /**
     * Carried so a correction can preserve the fields the dialog does not offer.
     * Sending back a default would quietly reclassify a warm-up set as working, or
     * drop an RIR the user recorded.
     */
    val setCategory: SetCategory,
    val rir: BigDecimal?,
)

/** Presentation model for one snapshotted exercise and its sets (read-only). */
data class WorkoutDetailExerciseRow(
    val id: UUID,
    val position: Int,
    val name: String,
    val notes: String?,
    val sets: List<WorkoutDetailSetRow>,
)

/**
 * A set the user is correcting, with the text currently in the fields.
 *
 * The text is held as typed rather than parsed on every keystroke, so a partially
 * entered number such as "8." is not thrown away mid-edit. [error] is set only
 * when a save is attempted with something unusable.
 *
 * The same model covers all three corrections ADR-0018 permits, because they are
 * one act with one set of rules rather than three features. [setId] is null when
 * the set is being added: a set that was performed but never logged has no row to
 * point at yet. [workoutExerciseId] is always present, since every correction
 * belongs to exactly one snapshotted exercise.
 */
data class SetCorrection(
    val workoutExerciseId: UUID,
    val setId: UUID?,
    /** The number this set will carry: the existing one, or the next free one. */
    val setNumber: Int,
    val exerciseName: String,
    val weight: String,
    val repetitions: String,
    val rpe: String,
    val error: String? = null,
    /**
     * True once delete has been asked for and not yet confirmed. Held here rather
     * than in a second dialog so the destructive step happens on the surface the
     * user is already looking at, with the values still in front of them.
     */
    val confirmingDelete: Boolean = false,
) {
    /** A set being added has no row yet; everything else is a correction to one. */
    val isNew: Boolean get() = setId == null
}

/** Immutable state for the workout detail screen. */
sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState

    /** No completed workout exists for this id (e.g. discarded, in-progress, or deleted). */
    data object NotFound : WorkoutDetailUiState

    data class Success(
        val date: String,
        val duration: String,
        val typeLabel: String,
        val notes: String?,
        val exercises: List<WorkoutDetailExerciseRow>,
        /** Non-null while a correction dialog is open (ADR-0018). */
        val correction: SetCorrection? = null,
    ) : WorkoutDetailUiState
}

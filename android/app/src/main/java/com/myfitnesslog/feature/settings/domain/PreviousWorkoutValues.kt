package com.myfitnesslog.feature.settings.domain

/**
 * Where the PREVIOUS column sources its values during an active workout.
 *
 * - [ANY_WORKOUT] — the most recent COMPLETED workout containing the exercise,
 *   regardless of routine (overload follows the exercise). This is the default.
 * - [SAME_ROUTINE] — the most recent COMPLETED workout of the *current routine*
 *   that contains the exercise, so PREVIOUS reflects how the exercise was
 *   performed the last time you ran this specific routine.
 *
 * Both strategies consider only COMPLETED sessions; IN_PROGRESS and DISCARDED are
 * never eligible. See docs/internal/V2/V2_PREVIOUS_PERFORMANCE_CONTRACT.md.
 */
enum class PreviousWorkoutValues {
    ANY_WORKOUT,
    SAME_ROUTINE,
    ;

    companion object {
        /** The value applied until the user chooses otherwise (spec default). */
        val DEFAULT: PreviousWorkoutValues = ANY_WORKOUT

        /** Parse a persisted name back to the enum, falling back to [DEFAULT]. */
        fun fromStorage(name: String?): PreviousWorkoutValues =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

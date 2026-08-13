package com.myfitnesslog.feature.history.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.myfitnesslog.core.data.local.SetCategory
import java.math.BigDecimal
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkoutDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val exerciseId = UUID.randomUUID()
    private val setId = UUID.randomUUID()

    private fun successState(
        notes: String? = null,
        exerciseNotes: String? = null,
        rpe: String? = "RPE 8",
    ) = WorkoutDetailUiState.Success(
        date = "21 Jul 2026",
        duration = "1h 05m",
        typeLabel = "Routine Workout",
        notes = notes,
        exercises = listOf(
            WorkoutDetailExerciseRow(
                id = exerciseId,
                position = 1,
                name = "Squat",
                notes = exerciseNotes,
                sets = listOf(
                    WorkoutDetailSetRow(
                        id = setId,
                        setNumber = 1,
                        weightReps = "100 × 5",
                        category = "Working",
                        rpe = rpe,
                        weight = BigDecimal("100"),
                        repetitions = 5,
                        rpeValue = BigDecimal("8"),
                        setCategory = SetCategory.WORKING,
                        rir = null,
                    ),
                ),
            ),
        ),
    )

    @Test
    fun rendersMetadata() {
        composeRule.setContent { WorkoutDetailContent(successState()) }
        composeRule.onNodeWithTag(WorkoutDetailTestTags.METADATA).assertIsDisplayed()
        composeRule.onNodeWithText("21 Jul 2026").assertIsDisplayed()
        composeRule.onNodeWithText("1h 05m").assertIsDisplayed()
        composeRule.onNodeWithText("Routine Workout").assertIsDisplayed()
    }

    @Test
    fun rendersExerciseAndSet() {
        composeRule.setContent { WorkoutDetailContent(successState(exerciseNotes = "Go deep")) }
        composeRule.onNodeWithTag(WorkoutDetailTestTags.exercise(exerciseId)).assertIsDisplayed()
        composeRule.onNodeWithText("1. Squat").assertIsDisplayed()
        composeRule.onNodeWithText("Go deep").assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutDetailTestTags.set(setId)).assertIsDisplayed()
        composeRule.onNodeWithText("Set 1").assertIsDisplayed()
        composeRule.onNodeWithText("100 × 5").assertIsDisplayed()
        composeRule.onNodeWithText("Working · RPE 8").assertIsDisplayed()
    }

    @Test
    fun rendersWorkoutNotesWhenPresent() {
        composeRule.setContent { WorkoutDetailContent(successState(notes = "Great session")) }
        composeRule.onNodeWithTag(WorkoutDetailTestTags.NOTES).assertIsDisplayed()
        composeRule.onNodeWithText("Great session").assertIsDisplayed()
    }

    @Test
    fun showsNotFoundState() {
        composeRule.setContent { WorkoutDetailContent(WorkoutDetailUiState.NotFound) }
        composeRule.onNodeWithTag(WorkoutDetailTestTags.NOT_FOUND).assertIsDisplayed()
    }

    // --- The correction dialog (ADR-0018) ----------------------------------

    private fun correctionState(
        error: String? = null,
        confirmingDelete: Boolean = false,
    ) = successState().copy(
        correction = SetCorrection(
            workoutExerciseId = exerciseId,
            setId = setId,
            setNumber = 1,
            exerciseName = "Squat",
            weight = "100",
            repetitions = "5",
            rpe = "8",
            error = error,
            confirmingDelete = confirmingDelete,
        ),
    )

    /** The dialog as it opens for a set that was performed but never logged. */
    private fun addState() = successState().copy(
        correction = SetCorrection(
            workoutExerciseId = exerciseId,
            setId = null,
            setNumber = 2,
            exerciseName = "Squat",
            weight = "",
            repetitions = "",
            rpe = "",
        ),
    )

    @Test
    fun tappingASetAsksToCorrectIt() {
        var corrected: UUID? = null
        composeRule.setContent {
            WorkoutDetailContent(successState(), onCorrectSet = { corrected = it })
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.set(setId)).performClick()

        assertEquals(setId, corrected)
    }

    /** No dialog until one is asked for: the screen is a record first. */
    @Test
    fun noDialogIsShownByDefault() {
        composeRule.setContent { WorkoutDetailContent(successState()) }
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_DIALOG).assertDoesNotExist()
    }

    @Test
    fun theDialogPreFillsTheRecordedValues() {
        composeRule.setContent { WorkoutDetailContent(correctionState()) }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_WEIGHT)
            .assertTextContains("100")
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_REPS)
            .assertTextContains("5")
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_RPE)
            .assertTextContains("8")
    }

    /**
     * The boundary ADR-0018 draws has to be visible, not enforced by an error
     * after the fact. The exercise name is a heading in the dialog, never a field.
     */
    @Test
    fun theDialogOffersNoWayToRewriteThePlan() {
        composeRule.setContent { WorkoutDetailContent(correctionState()) }

        composeRule.onNodeWithText("Squat").assertIsDisplayed()
        composeRule.onNodeWithText("Target sets", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Exercise name", substring = true).assertDoesNotExist()
    }

    @Test
    fun editingAFieldIsReportedUpwards() {
        var typedWeight: String? = null
        composeRule.setContent {
            WorkoutDetailContent(correctionState(), onWeightChange = { typedWeight = it })
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_WEIGHT).performTextReplacement("105")

        assertEquals("105", typedWeight)
    }

    @Test
    fun savingIsReportedUpwards() {
        var saved = false
        composeRule.setContent {
            WorkoutDetailContent(correctionState(), onSave = { saved = true })
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_SAVE).performClick()

        assertTrue(saved)
    }

    @Test
    fun cancellingIsReportedUpwards() {
        var dismissed = false
        composeRule.setContent {
            WorkoutDetailContent(correctionState(), onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithText("Cancel").performClick()

        assertTrue(dismissed)
    }

    @Test
    fun anErrorIsShownInTheDialogRatherThanClosingIt() {
        composeRule.setContent {
            WorkoutDetailContent(correctionState(error = "Enter a rep count of 1 or more."))
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_DIALOG).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_ERROR)
            .assertTextContains("Enter a rep count of 1 or more.")
    }

    // --- Adding a set (ADR-0018) -------------------------------------------

    @Test
    fun eachExerciseOffersToAddAForgottenSet() {
        var addedTo: UUID? = null
        composeRule.setContent {
            WorkoutDetailContent(successState(), onAddSet = { addedTo = it })
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.addSet(exerciseId)).performClick()

        assertEquals(exerciseId, addedTo)
    }

    /**
     * Adding and editing share one dialog, so the heading and confirm label are
     * what tell the user which one they are in.
     */
    @Test
    fun theAddDialogNamesTheSetBeingAddedAndOpensEmpty() {
        composeRule.setContent { WorkoutDetailContent(addState()) }

        composeRule.onNodeWithText("Add set 2").assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_SAVE)
            .assertTextContains("Add")
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_WEIGHT)
            .assertTextContains("Weight (kg)")
    }

    /** There is nothing to delete on a set that does not exist yet. */
    @Test
    fun theAddDialogOffersNoDelete() {
        composeRule.setContent { WorkoutDetailContent(addState()) }
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_DELETE).assertDoesNotExist()
    }

    // --- Deleting a set (ADR-0018) -----------------------------------------

    @Test
    fun theEditDialogOffersDelete() {
        var requested = false
        composeRule.setContent {
            WorkoutDetailContent(correctionState(), onDeleteRequested = { requested = true })
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_DELETE).performClick()

        assertTrue(requested)
    }

    /**
     * The confirmation replaces the fields rather than stacking a second dialog on
     * top, and it says what will happen: the row goes from the server too.
     */
    @Test
    fun theConfirmationSaysWhatDeletingDoes() {
        composeRule.setContent { WorkoutDetailContent(correctionState(confirmingDelete = true)) }

        composeRule.onNodeWithText("Delete set 1?").assertIsDisplayed()
        composeRule.onNodeWithText("on the server", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_WEIGHT).assertDoesNotExist()
    }

    @Test
    fun confirmingDeleteIsReportedUpwards() {
        var confirmed = false
        composeRule.setContent {
            WorkoutDetailContent(
                correctionState(confirmingDelete = true),
                onDeleteConfirmed = { confirmed = true },
            )
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.CORRECTION_DELETE_CONFIRM).performClick()

        assertTrue(confirmed)
    }

    // --- Discarding the whole workout (a different size of act) --------------

    @Test
    fun theScreenOffersToDiscardTheWorkout() {
        var requested = false
        composeRule.setContent {
            WorkoutDetailContent(successState(), onDiscardRequested = { requested = true })
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.DISCARD).performClick()

        assertTrue(requested)
    }

    /** No dialog until asked: the screen is a record first. */
    @Test
    fun noDiscardDialogByDefault() {
        composeRule.setContent { WorkoutDetailContent(successState()) }
        composeRule.onNodeWithTag(WorkoutDetailTestTags.DISCARD_DIALOG).assertDoesNotExist()
    }

    /**
     * The confirmation says what it costs, including the two consequences a user
     * cannot see from this screen: other devices, and future suggestions.
     */
    @Test
    fun theDiscardConfirmationSaysWhatItCosts() {
        composeRule.setContent {
            WorkoutDetailContent(successState().copy(confirmingDiscard = true))
        }

        composeRule.onNodeWithText("Discard this workout?").assertIsDisplayed()
        composeRule.onNodeWithText("any other device", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("previous workouts suggest", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("cannot be undone", substring = true).assertIsDisplayed()
    }

    @Test
    fun confirmingDiscardIsReportedUpwards() {
        var confirmed = false
        composeRule.setContent {
            WorkoutDetailContent(
                successState().copy(confirmingDiscard = true),
                onDiscardConfirmed = { confirmed = true },
            )
        }

        composeRule.onNodeWithTag(WorkoutDetailTestTags.DISCARD_CONFIRM).performClick()

        assertTrue(confirmed)
    }

    @Test
    fun keepingTheWorkoutIsReportedUpwards() {
        var cancelled = false
        composeRule.setContent {
            WorkoutDetailContent(
                successState().copy(confirmingDiscard = true),
                onDiscardCancelled = { cancelled = true },
            )
        }

        composeRule.onNodeWithText("Keep").performClick()

        assertTrue(cancelled)
    }

    @Test
    fun keepingTheSetIsReportedUpwards() {
        var cancelled = false
        composeRule.setContent {
            WorkoutDetailContent(
                correctionState(confirmingDelete = true),
                onDeleteCancelled = { cancelled = true },
            )
        }

        composeRule.onNodeWithText("Keep").performClick()

        assertTrue(cancelled)
    }
}

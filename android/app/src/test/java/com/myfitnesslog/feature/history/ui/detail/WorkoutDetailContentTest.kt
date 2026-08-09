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

    private fun correctionState(error: String? = null) = successState().copy(
        correction = SetCorrection(
            setId = setId,
            setNumber = 1,
            exerciseName = "Squat",
            weight = "100",
            repetitions = "5",
            rpe = "8",
            error = error,
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
}

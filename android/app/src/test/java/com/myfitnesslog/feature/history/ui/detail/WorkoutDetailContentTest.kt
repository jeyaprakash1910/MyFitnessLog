package com.myfitnesslog.feature.history.ui.detail

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
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
}

package com.myfitnesslog.feature.history.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkoutHistoryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val workoutId = UUID.randomUUID()

    private fun item(
        id: UUID = workoutId,
        typeLabel: String = "Routine Workout",
        notesPreview: String? = null,
    ) = WorkoutHistoryItem(
        id = id,
        date = "21 Jul 2026",
        duration = "1h 05m",
        typeLabel = typeLabel,
        exercisesLabel = "3 exercises",
        notesPreview = notesPreview,
    )

    private fun content(
        uiState: WorkoutHistoryUiState,
        onOpen: (UUID) -> Unit = {},
    ) = composeRule.setContent {
        WorkoutHistoryContent(uiState = uiState, onOpenWorkout = onOpen)
    }

    @Test
    fun showsEmptyState() {
        content(WorkoutHistoryUiState.Empty)
        composeRule.onNodeWithTag(WorkoutHistoryTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun rendersWorkoutItemFields() {
        content(WorkoutHistoryUiState.Success(listOf(item(notesPreview = "Felt strong"))))
        composeRule.onNodeWithTag(WorkoutHistoryTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("21 Jul 2026").assertIsDisplayed()
        composeRule.onNodeWithText("1h 05m").assertIsDisplayed()
        composeRule.onNodeWithText("Routine Workout · 3 exercises").assertIsDisplayed()
        composeRule.onNodeWithText("Felt strong").assertIsDisplayed()
    }

    @Test
    fun rowClickInvokesCallbackWithId() {
        var opened: UUID? = null
        content(WorkoutHistoryUiState.Success(listOf(item())), onOpen = { opened = it })

        composeRule.onNodeWithTag(WorkoutHistoryTestTags.row(workoutId)).performClick()

        assertEquals(workoutId, opened)
    }
}

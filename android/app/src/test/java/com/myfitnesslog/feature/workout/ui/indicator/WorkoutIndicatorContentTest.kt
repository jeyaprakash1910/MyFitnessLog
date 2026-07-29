package com.myfitnesslog.feature.workout.ui.indicator

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.myfitnesslog.feature.workout.ui.WorkoutTestTags
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
class WorkoutIndicatorContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun visible(
        elapsed: Duration = Duration.ofSeconds(65),
        routineName: String = "Push",
        currentExercise: String? = "Deadlift (Smith Machine)",
    ) = WorkoutIndicatorUiState.Visible(elapsed, routineName, currentExercise)

    @Test
    fun hiddenStateRendersNothing() {
        composeRule.setContent { WorkoutIndicatorContent(WorkoutIndicatorUiState.Hidden, onClick = {}) }
        composeRule.onAllNodesWithTag(WorkoutIndicatorTestTags.PILL).assertCountEquals(0)
    }

    @Test
    fun visibleStateRendersRoutineElapsedAndCurrentExercise() {
        composeRule.setContent { WorkoutIndicatorContent(visible(), onClick = {}) }
        composeRule.onNodeWithTag(WorkoutIndicatorTestTags.PILL).assertIsDisplayed()
        composeRule.onNodeWithText("Push").assertIsDisplayed()
        composeRule.onNodeWithText("1min 5s").assertIsDisplayed()
        composeRule.onNodeWithText("Deadlift (Smith Machine)").assertIsDisplayed()
    }

    @Test
    fun tappingTheResumeAffordanceResumesTheWorkout() {
        var clicked = false
        composeRule.setContent { WorkoutIndicatorContent(visible(), onClick = { clicked = true }) }
        composeRule.onNodeWithTag(WorkoutIndicatorTestTags.RESUME).performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun binAsksForConfirmationThenDiscards() {
        var discarded = false
        composeRule.setContent {
            WorkoutIndicatorContent(visible(), onClick = {}, onDiscard = { discarded = true })
        }
        composeRule.onNodeWithTag(WorkoutIndicatorTestTags.DISCARD).performClick()
        // Confirmation appears; nothing discarded yet.
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD_CONFIRM).assertIsDisplayed()
        assertEquals(false, discarded)
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD_CONFIRM_YES).performClick()
        assertEquals(true, discarded)
    }

    @Test
    fun cancelingTheBinConfirmationKeepsTheWorkout() {
        var discarded = false
        composeRule.setContent {
            WorkoutIndicatorContent(visible(), onClick = {}, onDiscard = { discarded = true })
        }
        composeRule.onNodeWithTag(WorkoutIndicatorTestTags.DISCARD).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD_CONFIRM_CANCEL).performClick()
        composeRule.onAllNodesWithTag(WorkoutTestTags.DISCARD_CONFIRM).assertCountEquals(0)
        assertEquals(false, discarded)
    }
}

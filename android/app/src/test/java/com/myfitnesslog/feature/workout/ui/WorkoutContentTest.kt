package com.myfitnesslog.feature.workout.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.feature.workout.domain.RestTimerState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkoutContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val exerciseId = UUID.randomUUID()
    private val setId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()

    private fun activeState(readOnly: Boolean = false, withSet: Boolean = true) = WorkoutUiState.Active(
        sessionId = sessionId,
        exercises = listOf(
            WorkoutExerciseUi(
                id = exerciseId,
                exerciseName = "Squat",
                targetSummary = "3 sets · 8–12 reps",
                sets = if (withSet) {
                    listOf(WorkoutSetUi(setId, 1, BigDecimal("80"), 8, SetCategory.WORKING, null, null, true))
                } else {
                    emptyList()
                },
            ),
        ),
        isReadOnly = readOnly,
    )

    private fun setContent(
        state: WorkoutUiState,
        elapsed: Duration = Duration.ZERO,
        restState: RestTimerState = RestTimerState.Idle,
        onStartManual: () -> Unit = {},
        onAddExercise: (UUID) -> Unit = {},
        onAddSet: (UUID, BigDecimal, Int, SetCategory, BigDecimal?) -> Unit = { _, _, _, _, _ -> },
        onDeleteSet: (UUID) -> Unit = {},
        onComplete: () -> Unit = {},
        onStartRest: (Int) -> Unit = {},
        onSkipRest: () -> Unit = {},
    ) = composeRule.setContent {
        WorkoutContent(
            uiState = state,
            elapsed = elapsed,
            restState = restState,
            onStartManual = onStartManual,
            onAddExercise = onAddExercise,
            onAddSet = onAddSet,
            onEditSet = { _, _, _, _, _, _, _ -> },
            onToggle = {},
            onDeleteSet = onDeleteSet,
            onComplete = onComplete,
            onDiscard = {},
            onStartRest = onStartRest,
            onSkipRest = onSkipRest,
            onCancelRest = {},
            onRestartRest = {},
        )
    }

    @Test
    fun showsEmptyStateWhenNoActiveWorkout() {
        setContent(WorkoutUiState.NoActiveWorkout)
        composeRule.onNodeWithTag(WorkoutTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun rendersExercisesSetsAndControls() {
        setContent(activeState())
        composeRule.onNodeWithTag(WorkoutTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.setRow(setId)).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.addSet(exerciseId)).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.COMPLETE).assertIsDisplayed()
    }

    @Test
    fun addSetDialogInvokesCallback() {
        var captured: Triple<UUID, BigDecimal, Int>? = null
        setContent(activeState(withSet = false), onAddSet = { id, w, r, _, _ -> captured = Triple(id, w, r) })

        composeRule.onNodeWithTag(WorkoutTestTags.addSet(exerciseId)).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.DIALOG_WEIGHT).performTextInput("100")
        composeRule.onNodeWithTag(WorkoutTestTags.DIALOG_REPS).performTextInput("5")
        composeRule.onNodeWithTag(WorkoutTestTags.DIALOG_CONFIRM).performClick()

        assertEquals(Triple(exerciseId, BigDecimal("100"), 5), captured)
    }

    @Test
    fun deleteSetInvokesCallback() {
        var deleted: UUID? = null
        setContent(activeState(), onDeleteSet = { deleted = it })
        composeRule.onNodeWithTag(WorkoutTestTags.deleteSet(setId)).performClick()
        assertEquals(setId, deleted)
    }

    @Test
    fun completeInvokesCallback() {
        var completed = false
        setContent(activeState(), onComplete = { completed = true })
        composeRule.onNodeWithTag(WorkoutTestTags.COMPLETE).performClick()
        assertEquals(true, completed)
    }

    @Test
    fun readOnlyStateHidesMutatingControls() {
        setContent(activeState(readOnly = true))
        composeRule.onAllNodesWithTag(WorkoutTestTags.addSet(exerciseId)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(WorkoutTestTags.COMPLETE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(WorkoutTestTags.deleteSet(setId)).assertCountEquals(0)
    }

    // --- Timers ---

    @Test
    fun rendersElapsedTime() {
        setContent(activeState(), elapsed = Duration.ofSeconds(65))
        composeRule.onNodeWithTag(WorkoutTestTags.ELAPSED).assertIsDisplayed()
        composeRule.onNodeWithText("01:05").assertIsDisplayed()
    }

    @Test
    fun restIdleShowsStartAndInvokesDefault() {
        var started: Int? = null
        setContent(activeState(), restState = RestTimerState.Idle, onStartRest = { started = it })
        composeRule.onNodeWithTag(WorkoutTestTags.REST_START).performClick()
        assertEquals(90, started)
    }

    @Test
    fun restRunningShowsRemainingAndSkip() {
        var skipped = false
        setContent(activeState(), restState = RestTimerState.Running(30, 90), onSkipRest = { skipped = true })
        composeRule.onNodeWithTag(WorkoutTestTags.REST_REMAINING).assertIsDisplayed()
        composeRule.onNodeWithText("Rest: 30s").assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
        assertEquals(true, skipped)
    }

    @Test
    fun restFinishedShowsCompletionUi() {
        setContent(activeState(), restState = RestTimerState.Finished)
        composeRule.onNodeWithTag(WorkoutTestTags.REST_DONE).assertIsDisplayed()
    }

    // --- Manual workouts ---

    @Test
    fun noActiveWorkoutOffersStartManual() {
        var started = false
        setContent(WorkoutUiState.NoActiveWorkout, onStartManual = { started = true })
        composeRule.onNodeWithTag(WorkoutTestTags.START_MANUAL).performClick()
        assertEquals(true, started)
    }

    @Test
    fun activeWorkoutOffersAddExerciseWithSessionId() {
        var addedTo: UUID? = null
        setContent(activeState(), onAddExercise = { addedTo = it })
        composeRule.onNodeWithTag(WorkoutTestTags.ADD_EXERCISE).performClick()
        assertEquals(sessionId, addedTo)
    }
}

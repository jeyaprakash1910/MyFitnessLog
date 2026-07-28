package com.myfitnesslog.feature.workout.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
    private val sessionId = UUID.randomUUID()
    private val completedKey = "set:${UUID.randomUUID()}"
    private val plannedKey = "draft:${UUID.randomUUID()}"

    private fun completedRow(setNumber: Int = 1) = WorkoutSetRowUi(
        rowKey = completedKey,
        setNumber = setNumber,
        weight = BigDecimal("80"),
        repetitions = 8,
        rpe = null,
        rir = null,
        setCategory = SetCategory.WORKING,
        isCompleted = true,
    )

    private fun plannedRow(setNumber: Int = 2) = WorkoutSetRowUi(
        rowKey = plannedKey,
        setNumber = setNumber,
        weight = null,
        repetitions = null,
        rpe = null,
        rir = null,
        setCategory = SetCategory.WORKING,
        isCompleted = false,
    )

    private fun activeState(readOnly: Boolean = false, rows: List<WorkoutSetRowUi> = listOf(completedRow(), plannedRow())) =
        WorkoutUiState.Active(
            sessionId = sessionId,
            exercises = listOf(
                WorkoutExerciseUi(id = exerciseId, exerciseName = "Squat", targetSummary = "3 sets · 8–12 reps", restSeconds = 90, rows = rows),
            ),
            isReadOnly = readOnly,
        )

    private fun setContent(
        state: WorkoutUiState,
        elapsed: Duration = Duration.ZERO,
        restState: RestTimerState = RestTimerState.Idle,
        onStartManual: () -> Unit = {},
        onAddExercise: (UUID) -> Unit = {},
        onAddSet: (UUID) -> Unit = {},
        onToggleComplete: (UUID, String, String, String) -> Unit = { _, _, _, _ -> },
        onCommitRow: (UUID, String, String, String) -> Unit = { _, _, _, _ -> },
        onRpeSelected: (UUID, String, BigDecimal?) -> Unit = { _, _, _ -> },
        onDeleteRow: (UUID, String) -> Unit = { _, _ -> },
        onMoveExerciseUp: (UUID) -> Unit = {},
        onMoveExerciseDown: (UUID) -> Unit = {},
        onRemoveExercise: (UUID) -> Unit = {},
        onSetExerciseRest: (UUID, Int) -> Unit = { _, _ -> },
        onComplete: () -> Unit = {},
        onDiscard: () -> Unit = {},
        onAdjustRest: (Int) -> Unit = {},
        onSkipRest: () -> Unit = {},
    ) = composeRule.setContent {
        WorkoutContent(
            uiState = state,
            elapsed = elapsed,
            restState = restState,
            onStartManual = onStartManual,
            onAddExercise = onAddExercise,
            onAddSet = onAddSet,
            onToggleComplete = onToggleComplete,
            onCommitRow = onCommitRow,
            onRpeSelected = onRpeSelected,
            onDeleteRow = onDeleteRow,
            onMoveExerciseUp = onMoveExerciseUp,
            onMoveExerciseDown = onMoveExerciseDown,
            onRemoveExercise = onRemoveExercise,
            onSetExerciseRest = onSetExerciseRest,
            onComplete = onComplete,
            onDiscard = onDiscard,
            onAdjustRest = onAdjustRest,
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
    fun rendersInlineRowsAndControls() {
        setContent(activeState())
        composeRule.onNodeWithTag(WorkoutTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.COMPLETE).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.row(completedKey)).assertExists()
        composeRule.onNodeWithTag(WorkoutTestTags.row(plannedKey)).assertExists()
        composeRule.onNodeWithTag(WorkoutTestTags.LIST)
            .performScrollToNode(hasTestTag(WorkoutTestTags.addSet(exerciseId)))
        composeRule.onNodeWithTag(WorkoutTestTags.addSet(exerciseId)).assertIsDisplayed()
    }

    @Test
    fun plannedRowsRenderFromState() {
        // A fresh exercise with only planned rows (no completed sets).
        setContent(activeState(rows = listOf(plannedRow(setNumber = 1))))
        composeRule.onNodeWithTag(WorkoutTestTags.row(plannedKey)).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.weight(plannedKey)).assertIsDisplayed()
    }

    @Test
    fun togglingAPlannedRowCommitsTypedValues() {
        var captured: Triple<String, String, String>? = null
        setContent(
            activeState(rows = listOf(plannedRow(setNumber = 1))),
            onToggleComplete = { _, key, w, r -> captured = Triple(key, w, r) },
        )
        composeRule.onNodeWithTag(WorkoutTestTags.weight(plannedKey)).performTextInput("100")
        composeRule.onNodeWithTag(WorkoutTestTags.reps(plannedKey)).performTextInput("5")
        composeRule.onNodeWithTag(WorkoutTestTags.toggle(plannedKey)).performClick()
        assertEquals(Triple(plannedKey, "100", "5"), captured)
    }

    @Test
    fun editingAWeightFieldCommitsOnFocusLoss() {
        var committed: Triple<String, String, String>? = null
        setContent(activeState(), onCommitRow = { _, key, w, r -> committed = Triple(key, w, r) })
        // Focus the completed row's weight, then move focus to the reps field -> commit.
        composeRule.onNodeWithTag(WorkoutTestTags.weight(completedKey)).performTextInput("85")
        composeRule.onNodeWithTag(WorkoutTestTags.reps(completedKey)).performClick()
        assertEquals(completedKey, committed?.first)
    }

    @Test
    fun tappingRpeCellOpensTheSheetWithContext() {
        // The sheet's Done action is verified end-to-end in WorkoutViewModelTest
        // (onRpeSelected); here we verify the sheet opens and renders its controls.
        setContent(activeState(rows = listOf(plannedRow(setNumber = 1))))
        composeRule.onNodeWithTag(WorkoutTestTags.rpe(plannedKey)).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.RPE_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.rpeValue("8.5")).assertExists()
        composeRule.onNodeWithTag(WorkoutTestTags.RPE_DONE).assertExists()
        composeRule.onNodeWithTag(WorkoutTestTags.RPE_CANCEL).assertExists()
    }

    @Test
    fun rpeSheetIsHiddenUntilOpened() {
        setContent(activeState(rows = listOf(plannedRow(setNumber = 1))))
        composeRule.onAllNodesWithTag(WorkoutTestTags.RPE_SHEET).assertCountEquals(0)
    }

    @Test
    fun previousColumnShowsValueOrDash() {
        val withPrev = plannedRow(setNumber = 1).copy(previous = "80kg × 8 @ 7 rpe")
        val withoutPrev = completedRow(setNumber = 2).copy(previous = null)
        setContent(activeState(rows = listOf(withPrev, withoutPrev)))
        composeRule.onNodeWithTag(WorkoutTestTags.previous(withPrev.rowKey)).assertIsDisplayed()
        composeRule.onNodeWithText("80kg × 8 @ 7 rpe").assertIsDisplayed()
        // Missing previous renders as a dash.
        composeRule.onNodeWithTag(WorkoutTestTags.previous(withoutPrev.rowKey)).assertIsDisplayed()
    }

    @Test
    fun completedRowShowsItsRpeValue() {
        val row = completedRow().copy(rpe = BigDecimal("9"))
        setContent(activeState(rows = listOf(row)))
        composeRule.onNodeWithTag(WorkoutTestTags.rpe(completedKey)).assertIsDisplayed()
        composeRule.onNodeWithText("9").assertIsDisplayed()
    }

    @Test
    fun completingViaCheckboxDoesNotRequireRpe() {
        // The ✓ path completes without opening the RPE sheet (RPE optional).
        var toggled: String? = null
        setContent(
            activeState(rows = listOf(plannedRow(setNumber = 1))),
            onToggleComplete = { _, key, _, _ -> toggled = key },
        )
        composeRule.onNodeWithTag(WorkoutTestTags.toggle(plannedKey)).performClick()
        composeRule.onAllNodesWithTag(WorkoutTestTags.RPE_SHEET).assertCountEquals(0)
        assertEquals(plannedKey, toggled)
    }

    @Test
    fun addSetInvokesCallback() {
        var added: UUID? = null
        setContent(activeState(), onAddSet = { added = it })
        composeRule.onNodeWithTag(WorkoutTestTags.LIST)
            .performScrollToNode(hasTestTag(WorkoutTestTags.addSet(exerciseId)))
        composeRule.onNodeWithTag(WorkoutTestTags.addSet(exerciseId)).performClick()
        assertEquals(exerciseId, added)
    }

    @Test
    fun discardAsksForConfirmationBeforeDiscarding() {
        var discarded = false
        setContent(activeState(), onDiscard = { discarded = true })
        composeRule.onNodeWithTag(WorkoutTestTags.LIST)
            .performScrollToNode(hasTestTag(WorkoutTestTags.DISCARD))
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD).performClick()
        // A confirmation appears; nothing is discarded yet.
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD_CONFIRM).assertIsDisplayed()
        assertEquals(false, discarded)
        // Confirming discards.
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD_CONFIRM_YES).performClick()
        assertEquals(true, discarded)
    }

    @Test
    fun cancelingTheDiscardConfirmationKeepsTheWorkout() {
        var discarded = false
        setContent(activeState(), onDiscard = { discarded = true })
        composeRule.onNodeWithTag(WorkoutTestTags.LIST)
            .performScrollToNode(hasTestTag(WorkoutTestTags.DISCARD))
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.DISCARD_CONFIRM_CANCEL).performClick()
        composeRule.onAllNodesWithTag(WorkoutTestTags.DISCARD_CONFIRM).assertCountEquals(0)
        assertEquals(false, discarded)
    }

    @Test
    fun completeInvokesCallback() {
        var completed = false
        setContent(activeState(), onComplete = { completed = true })
        composeRule.onNodeWithTag(WorkoutTestTags.COMPLETE).performClick()
        assertEquals(true, completed)
    }

    @Test
    fun exerciseManagementControlsInvokeCallbacks() {
        var up: UUID? = null
        var down: UUID? = null
        var removed: UUID? = null
        setContent(
            activeState(),
            onMoveExerciseUp = { up = it },
            onMoveExerciseDown = { down = it },
            onRemoveExercise = { removed = it },
        )
        // The controls live in the exercise ⋮ dropdown menu.
        composeRule.onNodeWithTag(WorkoutTestTags.exerciseMenu(exerciseId)).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.moveUp(exerciseId)).performClick()
        assertEquals(exerciseId, up)

        composeRule.onNodeWithTag(WorkoutTestTags.exerciseMenu(exerciseId)).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.moveDown(exerciseId)).performClick()
        assertEquals(exerciseId, down)

        composeRule.onNodeWithTag(WorkoutTestTags.exerciseMenu(exerciseId)).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.removeExercise(exerciseId)).performClick()
        assertEquals(exerciseId, removed)
    }

    @Test
    fun tappingRestTimerOpensTheWheelPicker() {
        // The Done → onSetExerciseRest wiring is covered end-to-end in WorkoutViewModelTest;
        // here we verify the picker opens with its controls (bottom-sheet Done is off the
        // Robolectric viewport, so it is asserted to exist rather than clicked — as with RPE).
        setContent(activeState())
        composeRule.onNodeWithTag(WorkoutTestTags.exerciseRest(exerciseId)).performClick()
        composeRule.onNodeWithTag(WorkoutTestTags.REST_PICKER).assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.REST_PICKER_DONE).assertExists()
    }

    @Test
    fun readOnlyStateHidesTheRestPicker() {
        setContent(activeState(readOnly = true))
        composeRule.onNodeWithTag(WorkoutTestTags.exerciseRest(exerciseId)).performClick()
        composeRule.onAllNodesWithTag(WorkoutTestTags.REST_PICKER).assertCountEquals(0)
    }

    @Test
    fun readOnlyStateHidesMutatingControls() {
        setContent(activeState(readOnly = true))
        composeRule.onAllNodesWithTag(WorkoutTestTags.addSet(exerciseId)).assertCountEquals(0)
        composeRule.onAllNodesWithTag(WorkoutTestTags.COMPLETE).assertCountEquals(0)
        composeRule.onAllNodesWithTag(WorkoutTestTags.exerciseMenu(exerciseId)).assertCountEquals(0)
    }

    @Test
    fun rendersElapsedTime() {
        setContent(activeState(), elapsed = Duration.ofSeconds(65))
        composeRule.onNodeWithTag(WorkoutTestTags.ELAPSED).assertIsDisplayed()
        composeRule.onNodeWithText("1min 5s").assertIsDisplayed()
    }

    @Test
    fun restIdleHidesTheBar() {
        // Auto-start model: no manual "Start rest"; the bar is hidden while Idle.
        setContent(activeState(), restState = RestTimerState.Idle)
        composeRule.onAllNodesWithTag(WorkoutTestTags.REST_REMAINING).assertCountEquals(0)
        composeRule.onAllNodesWithTag(WorkoutTestTags.REST_SKIP).assertCountEquals(0)
    }

    @Test
    fun restRunningShowsRemainingSkipAndAdjust() {
        var skipped = false
        var delta: Int? = null
        setContent(
            activeState(),
            restState = RestTimerState.Running(30, 90),
            onSkipRest = { skipped = true },
            onAdjustRest = { delta = it },
        )
        composeRule.onNodeWithTag(WorkoutTestTags.REST_REMAINING).assertIsDisplayed()
        composeRule.onNodeWithText("00:30").assertIsDisplayed()
        composeRule.onNodeWithTag(WorkoutTestTags.REST_PLUS).performClick()
        assertEquals(15, delta)
        composeRule.onNodeWithTag(WorkoutTestTags.REST_MINUS).performClick()
        assertEquals(-15, delta)
        composeRule.onNodeWithTag(WorkoutTestTags.REST_SKIP).performClick()
        assertEquals(true, skipped)
    }

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
        composeRule.onNodeWithTag(WorkoutTestTags.LIST)
            .performScrollToNode(hasTestTag(WorkoutTestTags.ADD_EXERCISE))
        composeRule.onNodeWithTag(WorkoutTestTags.ADD_EXERCISE).performClick()
        assertEquals(sessionId, addedTo)
    }
}

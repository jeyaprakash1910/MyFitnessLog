package com.myfitnesslog.feature.routine.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.myfitnesslog.feature.routine.ui.edit.RoutineEditContent
import com.myfitnesslog.feature.routine.ui.edit.RoutineEditTestTags
import com.myfitnesslog.feature.routine.ui.edit.RoutineEditUiState
import com.myfitnesslog.feature.routine.ui.edit.RoutineExerciseEditRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoutineEditContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val squatId = UUID.randomUUID()

    private fun successState() = RoutineEditUiState.Success(
        name = "Legs",
        exercises = listOf(
            RoutineExerciseEditRow(squatId, "Squat", "3 sets · 8–12 reps", 3, 8, 12, 90, null),
        ),
    )

    @Test
    fun showsNameAndExercise() {
        composeRule.setContent {
            RoutineEditContent(successState(), {}, {}, {}, {}, {}, { _, _, _, _, _, _ -> })
        }
        composeRule.onNodeWithTag(RoutineEditTestTags.NAME_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(RoutineEditTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun addExerciseButtonInvokesCallback() {
        var clicked = false
        composeRule.setContent {
            RoutineEditContent(successState(), {}, { clicked = true }, {}, {}, {}, { _, _, _, _, _, _ -> })
        }
        composeRule.onNodeWithTag(RoutineEditTestTags.ADD_EXERCISE).performClick()
        assertEquals(true, clicked)
    }

    @Test
    fun removeInvokesCallbackWithId() {
        var removed: UUID? = null
        composeRule.setContent {
            RoutineEditContent(successState(), {}, {}, {}, {}, { removed = it }, { _, _, _, _, _, _ -> })
        }
        composeRule.onNodeWithTag(RoutineEditTestTags.remove(squatId)).performClick()
        assertEquals(squatId, removed)
    }

    @Test
    fun moveDownInvokesCallbackWithId() {
        var moved: UUID? = null
        composeRule.setContent {
            RoutineEditContent(successState(), {}, {}, {}, { moved = it }, {}, { _, _, _, _, _, _ -> })
        }
        composeRule.onNodeWithTag(RoutineEditTestTags.moveDown(squatId)).performClick()
        assertEquals(squatId, moved)
    }

    @Test
    fun targetDialogSaveInvokesUpdateWithNewValues() {
        var capturedSets: Int? = null
        composeRule.setContent {
            RoutineEditContent(
                successState(), {}, {}, {}, {}, {},
                { _, sets, _, _, _, _ -> capturedSets = sets },
            )
        }

        composeRule.onNodeWithTag(RoutineEditTestTags.edit(squatId)).performClick()
        composeRule.onNodeWithTag(RoutineEditTestTags.TARGET_SETS).performTextReplacement("5")
        composeRule.onNodeWithTag(RoutineEditTestTags.TARGET_CONFIRM).performClick()

        assertEquals(5, capturedSets)
    }
}

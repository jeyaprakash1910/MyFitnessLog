package com.myfitnesslog.feature.routine.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.myfitnesslog.feature.routine.ui.detail.RoutineDetailContent
import com.myfitnesslog.feature.routine.ui.detail.RoutineDetailTestTags
import com.myfitnesslog.feature.routine.ui.detail.RoutineDetailUiState
import com.myfitnesslog.feature.routine.ui.detail.RoutineExerciseRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoutineDetailContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsNotFound() {
        composeRule.setContent { RoutineDetailContent(RoutineDetailUiState.NotFound, {}, {}) }
        composeRule.onNodeWithTag(RoutineDetailTestTags.NOT_FOUND).assertIsDisplayed()
    }

    @Test
    fun showsEmptyWhenNoExercises() {
        composeRule.setContent {
            RoutineDetailContent(RoutineDetailUiState.Success("Legs", emptyList()), {}, {})
        }
        composeRule.onNodeWithTag(RoutineDetailTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun rendersExercisesAndEditFab() {
        var edited = false
        val row = RoutineExerciseRow(UUID.randomUUID(), "Squat", "3 sets · 8–12 reps", null)
        composeRule.setContent {
            RoutineDetailContent(RoutineDetailUiState.Success("Legs", listOf(row)), { edited = true }, {})
        }

        composeRule.onNodeWithTag(RoutineDetailTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Squat").assertIsDisplayed()
        composeRule.onNodeWithTag(RoutineDetailTestTags.EDIT_FAB).performClick()
        assertEquals(true, edited)
    }
}

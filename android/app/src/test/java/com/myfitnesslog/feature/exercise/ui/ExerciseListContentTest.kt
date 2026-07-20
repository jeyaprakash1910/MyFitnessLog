package com.myfitnesslog.feature.exercise.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Compose UI tests for the stateless [ExerciseListContent], run on the JVM via
 * Robolectric (no emulator). Each state is asserted by its test tag / content.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsLoadingIndicator() {
        composeRule.setContent { ExerciseListContent(ExerciseListUiState.Loading) }

        composeRule.onNodeWithTag(ExerciseListTestTags.LOADING).assertIsDisplayed()
    }

    @Test
    fun showsEmptyState() {
        composeRule.setContent { ExerciseListContent(ExerciseListUiState.Empty) }

        composeRule.onNodeWithTag(ExerciseListTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun showsErrorState() {
        composeRule.setContent {
            ExerciseListContent(ExerciseListUiState.Error("network down"))
        }

        composeRule.onNodeWithTag(ExerciseListTestTags.ERROR).assertIsDisplayed()
        composeRule.onNodeWithText("network down").assertIsDisplayed()
    }

    @Test
    fun rendersExerciseList() {
        val items = listOf(
            ExerciseListItem(UUID.randomUUID(), "Bench Press", "Barbell"),
            ExerciseListItem(UUID.randomUUID(), "Pull Up", null),
        )
        composeRule.setContent {
            ExerciseListContent(ExerciseListUiState.Success(items))
        }

        composeRule.onNodeWithTag(ExerciseListTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Bench Press").assertIsDisplayed()
        composeRule.onNodeWithText("Pull Up").assertIsDisplayed()
    }
}

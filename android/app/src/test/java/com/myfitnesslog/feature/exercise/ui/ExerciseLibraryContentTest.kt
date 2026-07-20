package com.myfitnesslog.feature.exercise.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Compose UI tests for the stateless [ExerciseLibraryContent], run on the JVM via
 * Robolectric. Verifies the search field, category chips, list rendering, and
 * that user input is forwarded to the callbacks (state hoisting).
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseLibraryContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val chestId = UUID.randomUUID()

    private fun state(
        query: String = "",
        selectedCategoryId: UUID? = null,
        listState: ExerciseListUiState = ExerciseListUiState.Success(
            listOf(ExerciseListItem(UUID.randomUUID(), "Bench Press", "Barbell")),
        ),
    ) = ExerciseLibraryUiState(
        query = query,
        categories = listOf(CategoryFilterItem(chestId, "Chest")),
        selectedCategoryId = selectedCategoryId,
        listState = listState,
    )

    @Test
    fun showsSearchFieldChipsAndList() {
        composeRule.setContent {
            ExerciseLibraryContent(state(), onQueryChange = {}, onCategorySelected = {})
        }

        composeRule.onNodeWithTag(ExerciseListTestTags.SEARCH_FIELD).assertIsDisplayed()
        composeRule.onNodeWithTag(ExerciseListTestTags.CATEGORY_CHIP_ALL).assertIsDisplayed()
        composeRule.onNodeWithTag(ExerciseListTestTags.categoryChip(chestId)).assertIsDisplayed()
        composeRule.onNodeWithTag(ExerciseListTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Bench Press").assertIsDisplayed()
    }

    @Test
    fun typingForwardsToOnQueryChange() {
        var typed: String? = null
        composeRule.setContent {
            ExerciseLibraryContent(state(), onQueryChange = { typed = it }, onCategorySelected = {})
        }

        composeRule.onNodeWithTag(ExerciseListTestTags.SEARCH_FIELD).performTextInput("press")

        assertEquals("press", typed)
    }

    @Test
    fun selectingCategoryForwardsToOnCategorySelected() {
        var selected: UUID? = null
        var invoked = false
        composeRule.setContent {
            ExerciseLibraryContent(
                state(),
                onQueryChange = {},
                onCategorySelected = { selected = it; invoked = true },
            )
        }

        composeRule.onNodeWithTag(ExerciseListTestTags.categoryChip(chestId)).performClick()

        assertEquals(true, invoked)
        assertEquals(chestId, selected)
    }
}

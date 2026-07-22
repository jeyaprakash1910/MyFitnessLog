package com.myfitnesslog.feature.routine.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerContent
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerItem
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerTestTags
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerUiState
import com.myfitnesslog.feature.routine.ui.picker.PickerCategoryItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ExercisePickerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val squatId = UUID.randomUUID()

    @Test
    fun showsSearchAndList() {
        composeRule.setContent {
            ExercisePickerContent(
                ExercisePickerUiState(exercises = listOf(ExercisePickerItem(squatId, "Squat"))),
                {}, {},
            )
        }
        composeRule.onNodeWithTag(ExercisePickerTestTags.SEARCH).assertIsDisplayed()
        composeRule.onNodeWithTag(ExercisePickerTestTags.LIST).assertIsDisplayed()
    }

    @Test
    fun tappingCategoryChipInvokesFilter() {
        val chestId = UUID.randomUUID()
        var selected: UUID? = null
        var cleared = false
        composeRule.setContent {
            ExercisePickerContent(
                ExercisePickerUiState(
                    categories = listOf(PickerCategoryItem(chestId, "Chest")),
                    exercises = listOf(ExercisePickerItem(squatId, "Squat")),
                ),
                {},
                {},
                onCategorySelected = { if (it == null) cleared = true else selected = it },
            )
        }
        composeRule.onNodeWithTag(ExercisePickerTestTags.CATEGORY_ROW).assertIsDisplayed()
        composeRule.onNodeWithTag(ExercisePickerTestTags.categoryChip(chestId)).performClick()
        assertEquals(chestId, selected)

        composeRule.onNodeWithTag(ExercisePickerTestTags.CATEGORY_CHIP_ALL).performClick()
        assertEquals(true, cleared)
    }

    @Test
    fun tappingItemInvokesSelection() {
        var selected: UUID? = null
        composeRule.setContent {
            ExercisePickerContent(
                ExercisePickerUiState(exercises = listOf(ExercisePickerItem(squatId, "Squat"))),
                {},
                { selected = it },
            )
        }
        composeRule.onNodeWithTag(ExercisePickerTestTags.item(squatId)).performClick()
        assertEquals(squatId, selected)
    }
}

package com.myfitnesslog.feature.routine.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.myfitnesslog.feature.routine.ui.list.RoutineListContent
import com.myfitnesslog.feature.routine.ui.list.RoutineListItem
import com.myfitnesslog.feature.routine.ui.list.RoutineListTestTags
import com.myfitnesslog.feature.routine.ui.list.RoutineListUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoutineListContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val legsId = UUID.randomUUID()

    private fun content(
        uiState: RoutineListUiState,
        onCreate: (String) -> Unit = {},
        onOpen: (UUID) -> Unit = {},
        onDuplicate: (UUID) -> Unit = {},
        onDelete: (UUID) -> Unit = {},
    ) = composeRule.setContent {
        RoutineListContent(uiState, onCreate, onOpen, onDuplicate, onDelete)
    }

    @Test
    fun showsEmptyState() {
        content(RoutineListUiState.Empty)
        composeRule.onNodeWithTag(RoutineListTestTags.EMPTY).assertIsDisplayed()
    }

    @Test
    fun rendersRoutines() {
        content(RoutineListUiState.Success(listOf(RoutineListItem(legsId, "Legs"))))
        composeRule.onNodeWithTag(RoutineListTestTags.LIST).assertIsDisplayed()
        composeRule.onNodeWithText("Legs").assertIsDisplayed()
    }

    @Test
    fun createDialogFlowInvokesCallback() {
        var created: String? = null
        content(RoutineListUiState.Empty, onCreate = { created = it })

        composeRule.onNodeWithTag(RoutineListTestTags.FAB).performClick()
        composeRule.onNodeWithTag(RoutineListTestTags.CREATE_DIALOG_FIELD).performTextInput("Push")
        composeRule.onNodeWithTag(RoutineListTestTags.CREATE_DIALOG_CONFIRM).performClick()

        assertEquals("Push", created)
    }

    @Test
    fun overflowMenuDeleteInvokesCallback() {
        var deleted: UUID? = null
        content(
            RoutineListUiState.Success(listOf(RoutineListItem(legsId, "Legs"))),
            onDelete = { deleted = it },
        )

        composeRule.onNodeWithTag(RoutineListTestTags.overflow(legsId)).performClick()
        composeRule.onNodeWithTag(RoutineListTestTags.MENU_DELETE).performClick()

        assertEquals(legsId, deleted)
    }
}

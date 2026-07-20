package com.myfitnesslog.feature.routine.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

object ExercisePickerTestTags {
    const val SEARCH = "picker_search"
    const val LIST = "picker_list"
    fun item(id: UUID) = "picker_item_$id"
}

@Composable
fun ExercisePickerScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExercisePickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { onDone() }
    }

    ExercisePickerContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onExerciseSelected = viewModel::onExerciseSelected,
        modifier = modifier,
    )
}

@Composable
fun ExercisePickerContent(
    uiState: ExercisePickerUiState,
    onQueryChange: (String) -> Unit,
    onExerciseSelected: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = { Text("Search exercises") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(ExercisePickerTestTags.SEARCH),
        )
        LazyColumn(modifier = Modifier.fillMaxSize().testTag(ExercisePickerTestTags.LIST)) {
            items(uiState.exercises, key = { it.id }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    modifier = Modifier
                        .clickable { onExerciseSelected(exercise.id) }
                        .testTag(ExercisePickerTestTags.item(exercise.id)),
                )
                HorizontalDivider()
            }
        }
    }
}

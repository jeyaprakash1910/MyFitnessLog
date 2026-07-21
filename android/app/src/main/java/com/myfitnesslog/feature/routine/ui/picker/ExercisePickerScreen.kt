package com.myfitnesslog.feature.routine.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

object ExercisePickerTestTags {
    const val SEARCH = "picker_search"
    const val LIST = "picker_list"
    const val LOADING = "picker_loading"
    const val ERROR = "picker_error"
    const val RETRY = "picker_retry"
    const val EMPTY = "picker_empty"
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
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun ExercisePickerContent(
    uiState: ExercisePickerUiState,
    onQueryChange: (String) -> Unit,
    onExerciseSelected: (UUID) -> Unit,
    onRetry: () -> Unit = {},
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
        // These states are only reachable when the list is empty — a refresh
        // failing while rows are already cached must not disturb the user.
        when {
            uiState.showLoading -> Box(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.testTag(ExercisePickerTestTags.LOADING),
                )
            }

            uiState.showError -> Column(
                modifier = Modifier.fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = uiState.errorMessage.orEmpty(),
                    modifier = Modifier.testTag(ExercisePickerTestTags.ERROR),
                )
                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(ExercisePickerTestTags.RETRY),
                ) {
                    Text("Retry")
                }
            }

            uiState.showEmpty -> Text(
                text = if (uiState.query.isBlank()) {
                    "No exercises available."
                } else {
                    "No exercises match \"${uiState.query}\"."
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp)
                    .testTag(ExercisePickerTestTags.EMPTY),
            )
        }

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

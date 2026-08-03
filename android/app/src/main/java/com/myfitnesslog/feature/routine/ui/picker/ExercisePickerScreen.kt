package com.myfitnesslog.feature.routine.ui.picker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
    const val CATEGORY_ROW = "picker_category_row"
    const val CATEGORY_CHIP_ALL = "picker_category_chip_all"
    fun item(id: UUID) = "picker_item_$id"
    fun categoryChip(id: UUID) = "picker_category_chip_$id"
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
        onCategorySelected = viewModel::onCategorySelected,
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
    onCategorySelected: (UUID?) -> Unit = {},
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = uiState.query,
            onValueChange = onQueryChange,
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search exercises") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag(ExercisePickerTestTags.SEARCH),
        )

        CategoryChips(
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            onCategorySelected = onCategorySelected,
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
                text = when {
                    uiState.query.isNotBlank() -> "No exercises match \"${uiState.query}\"."
                    // A category filter narrowing to nothing is not the same as
                    // an empty library, and saying so would be misleading.
                    uiState.selectedCategoryId != null -> "No exercises in this category."
                    else -> "No exercises available."
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

/**
 * Category filter chips. Mirrors the exercise-library screen's row so the two
 * places a user browses exercises behave identically.
 */
@Composable
private fun CategoryChips(
    categories: List<PickerCategoryItem>,
    selectedCategoryId: UUID?,
    onCategorySelected: (UUID?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ExercisePickerTestTags.CATEGORY_ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") },
                modifier = Modifier.testTag(ExercisePickerTestTags.CATEGORY_CHIP_ALL),
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelected(category.id) },
                label = { Text(category.name) },
                modifier = Modifier.testTag(ExercisePickerTestTags.categoryChip(category.id)),
            )
        }
    }
}

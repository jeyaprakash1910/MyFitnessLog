package com.myfitnesslog.feature.exercise.ui

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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfitnesslog.core.ui.theme.MyFitnessLogTheme
import java.util.UUID

/** Test tags used by Compose UI tests to locate elements. */
object ExerciseListTestTags {
    const val LOADING = "exercise_list_loading"
    const val EMPTY = "exercise_list_empty"
    const val ERROR = "exercise_list_error"
    const val LIST = "exercise_list"
    const val SEARCH_FIELD = "exercise_search_field"
    const val CATEGORY_ROW = "exercise_category_row"
    const val CATEGORY_CHIP_ALL = "exercise_category_chip_all"
    fun categoryChip(id: UUID) = "exercise_category_chip_$id"
}

/**
 * Route composable: pulls the Hilt ViewModel, collects its state lifecycle-aware,
 * and delegates to the stateless [ExerciseLibraryContent].
 */
@Composable
fun ExerciseListScreen(
    modifier: Modifier = Modifier,
    viewModel: ExerciseListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExerciseLibraryContent(
        uiState = uiState,
        onQueryChange = viewModel::onQueryChange,
        onCategorySelected = viewModel::onCategorySelected,
        modifier = modifier,
    )
}

/**
 * Stateless rendering of the whole exercise-library screen: search field,
 * category chips, and the list area. Free of the ViewModel so it is directly
 * testable and previewable.
 */
@Composable
fun ExerciseLibraryContent(
    uiState: ExerciseLibraryUiState,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (UUID?) -> Unit,
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
                .testTag(ExerciseListTestTags.SEARCH_FIELD),
        )

        CategoryChips(
            categories = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            onCategorySelected = onCategorySelected,
        )

        ExerciseListContent(
            uiState = uiState.listState,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CategoryChips(
    categories: List<CategoryFilterItem>,
    selectedCategoryId: UUID?,
    onCategorySelected: (UUID?) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ExerciseListTestTags.CATEGORY_ROW),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId == null,
                onClick = { onCategorySelected(null) },
                label = { Text("All") },
                modifier = Modifier.testTag(ExerciseListTestTags.CATEGORY_CHIP_ALL),
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onCategorySelected(category.id) },
                label = { Text(category.name) },
                modifier = Modifier.testTag(ExerciseListTestTags.categoryChip(category.id)),
            )
        }
    }
}

/**
 * Stateless rendering of the list area's [ExerciseListUiState].
 */
@Composable
fun ExerciseListContent(
    uiState: ExerciseListUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            ExerciseListUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .testTag(ExerciseListTestTags.LOADING),
            )

            ExerciseListUiState.Empty -> CenteredMessage(
                text = "No exercises match.",
                testTag = ExerciseListTestTags.EMPTY,
            )

            is ExerciseListUiState.Error -> CenteredMessage(
                text = uiState.message,
                testTag = ExerciseListTestTags.ERROR,
            )

            is ExerciseListUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(ExerciseListTestTags.LIST),
            ) {
                items(uiState.exercises, key = { it.id }) { exercise ->
                    ListItem(
                        headlineContent = { Text(exercise.name) },
                        supportingContent = exercise.equipment?.let { { Text(it) } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(text: String, testTag: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .testTag(testTag),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ExerciseLibraryPreview() {
    MyFitnessLogTheme {
        ExerciseLibraryContent(
            uiState = ExerciseLibraryUiState(
                query = "",
                categories = listOf(
                    CategoryFilterItem(UUID.randomUUID(), "Chest"),
                    CategoryFilterItem(UUID.randomUUID(), "Back"),
                ),
                selectedCategoryId = null,
                listState = ExerciseListUiState.Success(
                    listOf(
                        ExerciseListItem(UUID.randomUUID(), "Bench Press", "Barbell"),
                        ExerciseListItem(UUID.randomUUID(), "Pull Up", null),
                    ),
                ),
            ),
            onQueryChange = {},
            onCategorySelected = {},
        )
    }
}

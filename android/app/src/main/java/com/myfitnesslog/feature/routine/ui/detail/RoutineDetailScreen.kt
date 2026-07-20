package com.myfitnesslog.feature.routine.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

object RoutineDetailTestTags {
    const val LIST = "routine_detail_list"
    const val EMPTY = "routine_detail_empty"
    const val NOT_FOUND = "routine_detail_not_found"
    const val EDIT_FAB = "routine_detail_edit_fab"
}

@Composable
fun RoutineDetailScreen(
    onEditRoutine: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoutineDetailContent(uiState = uiState, onEditRoutine = onEditRoutine, modifier = modifier)
}

@Composable
fun RoutineDetailContent(
    uiState: RoutineDetailUiState,
    onEditRoutine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            RoutineDetailUiState.Loading -> Unit

            RoutineDetailUiState.NotFound -> Text(
                text = "Routine not found.",
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .testTag(RoutineDetailTestTags.NOT_FOUND),
            )

            is RoutineDetailUiState.Success -> {
                if (uiState.exercises.isEmpty()) {
                    Text(
                        text = "No exercises in this routine yet.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp)
                            .testTag(RoutineDetailTestTags.EMPTY),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag(RoutineDetailTestTags.LIST),
                    ) {
                        items(uiState.exercises, key = { it.id }) { exercise ->
                            ListItem(
                                headlineContent = { Text(exercise.exerciseName) },
                                supportingContent = {
                                    Text(
                                        buildString {
                                            append(exercise.targetSummary)
                                            exercise.notes?.let { append("\n").append(it) }
                                        },
                                    )
                                },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                FloatingActionButton(
                    onClick = onEditRoutine,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .testTag(RoutineDetailTestTags.EDIT_FAB),
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit routine")
                }
            }
        }
    }
}

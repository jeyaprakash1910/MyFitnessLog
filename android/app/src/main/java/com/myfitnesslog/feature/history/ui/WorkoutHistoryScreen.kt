package com.myfitnesslog.feature.history.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfitnesslog.core.ui.components.EmptyState
import java.util.UUID

object WorkoutHistoryTestTags {
    const val LIST = "history_list"
    const val EMPTY = "history_empty"
    fun row(id: UUID) = "history_row_$id"
}

@Composable
fun WorkoutHistoryScreen(
    onOpenWorkout: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutHistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutHistoryContent(
        uiState = uiState,
        onOpenWorkout = onOpenWorkout,
        modifier = modifier,
    )
}

@Composable
fun WorkoutHistoryContent(
    uiState: WorkoutHistoryUiState,
    onOpenWorkout: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            WorkoutHistoryUiState.Loading -> Unit
            WorkoutHistoryUiState.Empty -> EmptyState(
                icon = Icons.Filled.DateRange,
                title = "No completed workouts",
                description = "Finish a workout and it will show up here so you can track your progress over time.",
                modifier = Modifier.testTag(WorkoutHistoryTestTags.EMPTY),
            )

            is WorkoutHistoryUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(WorkoutHistoryTestTags.LIST),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(uiState.workouts, key = { it.id }) { workout ->
                    WorkoutHistoryRow(
                        workout = workout,
                        onOpen = { onOpenWorkout(workout.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkoutHistoryRow(
    workout: WorkoutHistoryItem,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "View workout details", onClick = onOpen)
            .testTag(WorkoutHistoryTestTags.row(workout.id)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = workout.date, style = MaterialTheme.typography.titleMedium)
                Text(text = workout.duration, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = "${workout.typeLabel} · ${workout.exercisesLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            workout.notesPreview?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

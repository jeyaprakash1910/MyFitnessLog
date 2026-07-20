package com.myfitnesslog.feature.history.ui.detail

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
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import java.util.UUID

object WorkoutDetailTestTags {
    const val CONTENT = "workout_detail_content"
    const val NOT_FOUND = "workout_detail_not_found"
    const val METADATA = "workout_detail_metadata"
    const val NOTES = "workout_detail_notes"
    fun exercise(id: UUID) = "workout_detail_exercise_$id"
    fun set(id: UUID) = "workout_detail_set_$id"
}

@Composable
fun WorkoutDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutDetailContent(uiState = uiState, modifier = modifier)
}

@Composable
fun WorkoutDetailContent(
    uiState: WorkoutDetailUiState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            WorkoutDetailUiState.Loading -> Unit
            WorkoutDetailUiState.NotFound -> Text(
                text = "This workout is no longer available.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .testTag(WorkoutDetailTestTags.NOT_FOUND),
            )

            is WorkoutDetailUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(WorkoutDetailTestTags.CONTENT),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { WorkoutMetadata(uiState) }
                items(uiState.exercises, key = { it.id }) { exercise ->
                    ExerciseCard(exercise)
                }
            }
        }
    }
}

@Composable
private fun WorkoutMetadata(state: WorkoutDetailUiState.Success) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WorkoutDetailTestTags.METADATA),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = state.date, style = MaterialTheme.typography.headlineSmall)
            Text(text = state.duration, style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            text = state.typeLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.notes?.let { notes ->
            Text(
                text = notes,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag(WorkoutDetailTestTags.NOTES),
            )
        }
    }
}

@Composable
private fun ExerciseCard(exercise: WorkoutDetailExerciseRow) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WorkoutDetailTestTags.exercise(exercise.id)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "${exercise.position}. ${exercise.name}",
                style = MaterialTheme.typography.titleMedium,
            )
            exercise.notes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (exercise.sets.isEmpty()) {
                Text(
                    text = "No sets recorded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                exercise.sets.forEach { set ->
                    HorizontalDivider()
                    SetRow(set)
                }
            }
        }
    }
}

@Composable
private fun SetRow(set: WorkoutDetailSetRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(WorkoutDetailTestTags.set(set.id)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Set ${set.setNumber}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = set.weightReps,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = buildString {
                append(set.category)
                set.rpe?.let { append(" · $it") }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

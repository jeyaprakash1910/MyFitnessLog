package com.myfitnesslog.feature.history.ui.detail

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
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
    const val CORRECTION_DIALOG = "workout_detail_correction_dialog"
    const val CORRECTION_WEIGHT = "workout_detail_correction_weight"
    const val CORRECTION_REPS = "workout_detail_correction_reps"
    const val CORRECTION_RPE = "workout_detail_correction_rpe"
    const val CORRECTION_SAVE = "workout_detail_correction_save"
    const val CORRECTION_ERROR = "workout_detail_correction_error"
    fun exercise(id: UUID) = "workout_detail_exercise_$id"
    fun set(id: UUID) = "workout_detail_set_$id"
}

@Composable
fun WorkoutDetailScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    WorkoutDetailContent(
        uiState = uiState,
        onCorrectSet = viewModel::onCorrectSet,
        onWeightChange = viewModel::onCorrectionWeightChange,
        onRepsChange = viewModel::onCorrectionRepsChange,
        onRpeChange = viewModel::onCorrectionRpeChange,
        onDismiss = viewModel::onCorrectionDismissed,
        onSave = viewModel::onCorrectionSaved,
        modifier = modifier,
    )
}

@Composable
fun WorkoutDetailContent(
    uiState: WorkoutDetailUiState,
    onCorrectSet: (UUID) -> Unit = {},
    onWeightChange: (String) -> Unit = {},
    onRepsChange: (String) -> Unit = {},
    onRpeChange: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    onSave: () -> Unit = {},
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
                    ExerciseCard(exercise, onCorrectSet)
                }
            }
        }
    }

    (uiState as? WorkoutDetailUiState.Success)?.correction?.let { correction ->
        CorrectionDialog(
            correction = correction,
            onWeightChange = onWeightChange,
            onRepsChange = onRepsChange,
            onRpeChange = onRpeChange,
            onDismiss = onDismiss,
            onSave = onSave,
        )
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
private fun ExerciseCard(exercise: WorkoutDetailExerciseRow, onCorrectSet: (UUID) -> Unit) {
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
                    SetRow(set, onCorrectSet)
                }
            }
        }
    }
}

@Composable
private fun SetRow(set: WorkoutDetailSetRow, onCorrectSet: (UUID) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // The whole row is the target. A set is a small amount of text and a
            // dedicated pencil per row would clutter a screen that is mostly read.
            .clickable { onCorrectSet(set.id) }
            .padding(vertical = 8.dp)
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

/**
 * The correction dialog (ADR-0018).
 *
 * Offers only the three values a correction is actually about. The exercise name
 * and set number are shown as a heading rather than as fields, which is how the
 * screen says "this part is not editable" without an error message: the planning
 * snapshot records what the plan was on the day and stays locked.
 *
 * RPE is explicitly optional, so clearing the field removes an effort score that
 * was entered by mistake.
 */
@Composable
private fun CorrectionDialog(
    correction: SetCorrection,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_DIALOG),
        title = { Text("Correct set ${correction.setNumber}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = correction.exerciseName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = correction.weight,
                    onValueChange = onWeightChange,
                    label = { Text("Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_WEIGHT),
                )
                OutlinedTextField(
                    value = correction.repetitions,
                    onValueChange = onRepsChange,
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_REPS),
                )
                OutlinedTextField(
                    value = correction.rpe,
                    onValueChange = onRpeChange,
                    label = { Text("RPE (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_RPE),
                )
                correction.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_ERROR),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_SAVE),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

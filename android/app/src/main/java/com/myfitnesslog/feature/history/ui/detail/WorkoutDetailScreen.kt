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
import androidx.compose.runtime.LaunchedEffect
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
    const val CORRECTION_DELETE = "workout_detail_correction_delete"
    const val CORRECTION_DELETE_CONFIRM = "workout_detail_correction_delete_confirm"
    const val DISCARD = "workout_detail_discard"
    const val DISCARD_DIALOG = "workout_detail_discard_dialog"
    const val DISCARD_CONFIRM = "workout_detail_discard_confirm"
    fun exercise(id: UUID) = "workout_detail_exercise_$id"
    fun set(id: UUID) = "workout_detail_set_$id"
    fun addSet(exerciseId: UUID) = "workout_detail_add_set_$exerciseId"
}

@Composable
fun WorkoutDetailScreen(
    onDiscarded: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: WorkoutDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Leave once the workout is gone. Staying would show "This workout is no longer
    // available" about the thing the user just deliberately removed, and make them
    // press back to escape a screen that exists only to describe an absence.
    val discarded by viewModel.discarded.collectAsStateWithLifecycle()
    LaunchedEffect(discarded) {
        if (discarded) onDiscarded()
    }
    WorkoutDetailContent(
        uiState = uiState,
        onCorrectSet = viewModel::onCorrectSet,
        onAddSet = viewModel::onAddSet,
        onWeightChange = viewModel::onCorrectionWeightChange,
        onRepsChange = viewModel::onCorrectionRepsChange,
        onRpeChange = viewModel::onCorrectionRpeChange,
        onDismiss = viewModel::onCorrectionDismissed,
        onSave = viewModel::onCorrectionSaved,
        onDeleteRequested = viewModel::onDeleteRequested,
        onDeleteCancelled = viewModel::onDeleteCancelled,
        onDeleteConfirmed = viewModel::onDeleteConfirmed,
        onDiscardRequested = viewModel::onDiscardRequested,
        onDiscardCancelled = viewModel::onDiscardCancelled,
        onDiscardConfirmed = viewModel::onDiscardConfirmed,
        modifier = modifier,
    )
}

@Composable
fun WorkoutDetailContent(
    uiState: WorkoutDetailUiState,
    onCorrectSet: (UUID) -> Unit = {},
    onAddSet: (UUID) -> Unit = {},
    onWeightChange: (String) -> Unit = {},
    onRepsChange: (String) -> Unit = {},
    onRpeChange: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    onSave: () -> Unit = {},
    onDeleteRequested: () -> Unit = {},
    onDeleteCancelled: () -> Unit = {},
    onDeleteConfirmed: () -> Unit = {},
    onDiscardRequested: () -> Unit = {},
    onDiscardCancelled: () -> Unit = {},
    onDiscardConfirmed: () -> Unit = {},
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
                    ExerciseCard(exercise, onCorrectSet, onAddSet)
                }
                item {
                    // Last on the screen, well below the sets. Removing the whole
                    // workout is a different order of thing from correcting one set,
                    // and it should not sit within reach of a thumb reading history.
                    TextButton(
                        onClick = onDiscardRequested,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                            .testTag(WorkoutDetailTestTags.DISCARD),
                    ) {
                        Text("Discard Workout", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    if ((uiState as? WorkoutDetailUiState.Success)?.confirmingDiscard == true) {
        AlertDialog(
            onDismissRequest = onDiscardCancelled,
            modifier = Modifier.testTag(WorkoutDetailTestTags.DISCARD_DIALOG),
            title = { Text("Discard this workout?") },
            text = {
                Text(
                    "It will be removed from your history here, on the web, and on " +
                        "any other device. Sets you logged in it stop counting towards " +
                        "what previous workouts suggest. This cannot be undone in the app.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onDiscardConfirmed,
                    modifier = Modifier.testTag(WorkoutDetailTestTags.DISCARD_CONFIRM),
                ) {
                    Text("Discard Workout", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDiscardCancelled) { Text("Keep") }
            },
        )
    }

    (uiState as? WorkoutDetailUiState.Success)?.correction?.let { correction ->
        CorrectionDialog(
            correction = correction,
            onWeightChange = onWeightChange,
            onRepsChange = onRepsChange,
            onRpeChange = onRpeChange,
            onDismiss = onDismiss,
            onSave = onSave,
            onDeleteRequested = onDeleteRequested,
            onDeleteCancelled = onDeleteCancelled,
            onDeleteConfirmed = onDeleteConfirmed,
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
private fun ExerciseCard(
    exercise: WorkoutDetailExerciseRow,
    onCorrectSet: (UUID) -> Unit,
    onAddSet: (UUID) -> Unit,
) {
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
            HorizontalDivider()
            // Sits under the sets rather than in the card header, so it reads as
            // "and then this one", which is what adding a forgotten set is.
            TextButton(
                onClick = { onAddSet(exercise.id) },
                modifier = Modifier.testTag(WorkoutDetailTestTags.addSet(exercise.id)),
            ) { Text("Add set") }
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
 *
 * One dialog serves editing and adding, because they ask for the same three
 * values under the same rules; only the heading and the confirm label differ.
 * Deleting is reachable only when editing, and only behind a confirmation, since
 * it is the one correction that removes a record instead of amending it.
 */
@Composable
private fun CorrectionDialog(
    correction: SetCorrection,
    onWeightChange: (String) -> Unit,
    onRepsChange: (String) -> Unit,
    onRpeChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onDeleteRequested: () -> Unit,
    onDeleteCancelled: () -> Unit,
    onDeleteConfirmed: () -> Unit,
) {
    if (correction.confirmingDelete) {
        AlertDialog(
            onDismissRequest = onDeleteCancelled,
            modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_DIALOG),
            title = { Text("Delete set ${correction.setNumber}?") },
            text = {
                Text(
                    "This removes the set from ${correction.exerciseName} on this " +
                        "workout, here and on the server. It cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onDeleteConfirmed,
                    modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_DELETE_CONFIRM),
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = onDeleteCancelled) { Text("Keep") }
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_DIALOG),
        title = {
            Text(
                if (correction.isNew) "Add set ${correction.setNumber}"
                else "Correct set ${correction.setNumber}",
            )
        },
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
            ) { Text(if (correction.isNew) "Add" else "Save") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Delete sits furthest from Save, on the dismiss side, because a
                // mis-tap here loses data that a mis-tap on Save does not.
                if (!correction.isNew) {
                    TextButton(
                        onClick = onDeleteRequested,
                        modifier = Modifier.testTag(WorkoutDetailTestTags.CORRECTION_DELETE),
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

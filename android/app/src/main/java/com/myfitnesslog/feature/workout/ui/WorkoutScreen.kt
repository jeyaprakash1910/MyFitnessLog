package com.myfitnesslog.feature.workout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.feature.workout.domain.RestTimerState
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

private const val DEFAULT_REST_SECONDS = 90

object WorkoutTestTags {
    const val LIST = "workout_list"
    const val EMPTY = "workout_empty"
    const val START_MANUAL = "workout_start_manual"
    const val ADD_EXERCISE = "workout_add_exercise"
    const val COMPLETE = "workout_complete"
    const val DISCARD = "workout_discard"
    const val ELAPSED = "workout_elapsed"
    const val REST_START = "rest_start"
    const val REST_REMAINING = "rest_remaining"
    const val REST_SKIP = "rest_skip"
    const val REST_CANCEL = "rest_cancel"
    const val REST_RESTART = "rest_restart"
    const val REST_DONE = "rest_done"
    fun addSet(exerciseId: UUID) = "workout_add_set_$exerciseId"
    fun setRow(setId: UUID) = "workout_set_$setId"
    fun toggle(setId: UUID) = "workout_toggle_$setId"
    fun editSet(setId: UUID) = "workout_edit_$setId"
    fun deleteSet(setId: UUID) = "workout_delete_$setId"
    const val DIALOG_WEIGHT = "set_weight"
    const val DIALOG_REPS = "set_reps"
    const val DIALOG_CONFIRM = "set_confirm"
}

private fun Duration.asClock(): String {
    val total = seconds.coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

@Composable
fun WorkoutScreen(
    onFinished: (WorkoutViewModel.Event) -> Unit,
    onAddExercise: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val elapsed by viewModel.elapsed.collectAsStateWithLifecycle()
    val restState by viewModel.restTimer.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { onFinished(it) }
    }

    WorkoutContent(
        uiState = uiState,
        elapsed = elapsed,
        restState = restState,
        onStartManual = viewModel::startManualWorkout,
        onAddExercise = onAddExercise,
        onAddSet = viewModel::addSet,
        onEditSet = viewModel::updateSet,
        onToggle = viewModel::toggleCompletion,
        onDeleteSet = viewModel::deleteSet,
        onComplete = viewModel::completeWorkout,
        onDiscard = viewModel::discardWorkout,
        onStartRest = viewModel::startRest,
        onSkipRest = viewModel::skipRest,
        onCancelRest = viewModel::cancelRest,
        onRestartRest = viewModel::restartRest,
        modifier = modifier,
    )
}

private sealed interface SetDialogTarget {
    data class Add(val exerciseId: UUID) : SetDialogTarget
    data class Edit(val set: WorkoutSetUi) : SetDialogTarget
}

@Composable
fun WorkoutContent(
    uiState: WorkoutUiState,
    elapsed: Duration,
    restState: RestTimerState,
    onStartManual: () -> Unit,
    onAddExercise: (UUID) -> Unit,
    onAddSet: (UUID, BigDecimal, Int, SetCategory, BigDecimal?) -> Unit,
    onEditSet: (UUID, BigDecimal, Int, SetCategory, BigDecimal?, BigDecimal?, Boolean) -> Unit,
    onToggle: (UUID) -> Unit,
    onDeleteSet: (UUID) -> Unit,
    onComplete: () -> Unit,
    onDiscard: () -> Unit,
    onStartRest: (Int) -> Unit,
    onSkipRest: () -> Unit,
    onCancelRest: () -> Unit,
    onRestartRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<SetDialogTarget?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            WorkoutUiState.Loading -> Unit

            WorkoutUiState.NoActiveWorkout -> Column(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "No active workout.",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(WorkoutTestTags.EMPTY),
                )
                Button(
                    onClick = onStartManual,
                    modifier = Modifier.testTag(WorkoutTestTags.START_MANUAL),
                ) { Text("Start empty workout") }
            }

            is WorkoutUiState.Active -> Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = elapsed.asClock(),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).testTag(WorkoutTestTags.ELAPSED),
                )

                if (!uiState.isReadOnly) {
                    RestTimerBar(
                        restState = restState,
                        onStartRest = { onStartRest(DEFAULT_REST_SECONDS) },
                        onSkipRest = onSkipRest,
                        onCancelRest = onCancelRest,
                        onRestartRest = onRestartRest,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onComplete,
                            modifier = Modifier.testTag(WorkoutTestTags.COMPLETE),
                        ) { Text("Complete") }
                        OutlinedButton(
                            onClick = onDiscard,
                            modifier = Modifier.testTag(WorkoutTestTags.DISCARD),
                        ) { Text("Discard") }
                        OutlinedButton(
                            onClick = { onAddExercise(uiState.sessionId) },
                            modifier = Modifier.testTag(WorkoutTestTags.ADD_EXERCISE),
                        ) { Text("Add exercise") }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize().testTag(WorkoutTestTags.LIST)) {
                    uiState.exercises.forEach { exercise ->
                        item(key = exercise.id) {
                            ExerciseHeader(exercise)
                        }
                        items(exercise.sets, key = { it.id }) { set ->
                            SetRow(
                                set = set,
                                readOnly = uiState.isReadOnly,
                                onToggle = { onToggle(set.id) },
                                onEdit = { dialog = SetDialogTarget.Edit(set) },
                                onDelete = { onDeleteSet(set.id) },
                            )
                        }
                        if (!uiState.isReadOnly) {
                            item(key = "add_${exercise.id}") {
                                TextButton(
                                    onClick = { dialog = SetDialogTarget.Add(exercise.id) },
                                    modifier = Modifier.testTag(WorkoutTestTags.addSet(exercise.id)),
                                ) { Text("Add set") }
                            }
                        }
                        item(key = "divider_${exercise.id}") { HorizontalDivider() }
                    }
                }
            }
        }
    }

    when (val target = dialog) {
        is SetDialogTarget.Add -> SetDialog(
            initial = null,
            onConfirm = { weight, reps, category, rpe, _ ->
                onAddSet(target.exerciseId, weight, reps, category, rpe)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        is SetDialogTarget.Edit -> SetDialog(
            initial = target.set,
            onConfirm = { weight, reps, category, rpe, completed ->
                onEditSet(target.set.id, weight, reps, category, rpe, target.set.rir, completed)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun RestTimerBar(
    restState: RestTimerState,
    onStartRest: () -> Unit,
    onSkipRest: () -> Unit,
    onCancelRest: () -> Unit,
    onRestartRest: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (restState) {
            RestTimerState.Idle -> Button(
                onClick = onStartRest,
                modifier = Modifier.testTag(WorkoutTestTags.REST_START),
            ) { Text("Start rest") }

            is RestTimerState.Running -> {
                Text(
                    text = "Rest: ${restState.remainingSeconds}s",
                    modifier = Modifier.weight(1f).testTag(WorkoutTestTags.REST_REMAINING),
                )
                TextButton(onClick = onSkipRest, modifier = Modifier.testTag(WorkoutTestTags.REST_SKIP)) {
                    Text("Skip")
                }
                TextButton(onClick = onCancelRest, modifier = Modifier.testTag(WorkoutTestTags.REST_CANCEL)) {
                    Text("Cancel")
                }
            }

            RestTimerState.Finished -> {
                Text(
                    text = "Rest done",
                    modifier = Modifier.weight(1f).testTag(WorkoutTestTags.REST_DONE),
                )
                TextButton(onClick = onRestartRest, modifier = Modifier.testTag(WorkoutTestTags.REST_RESTART)) {
                    Text("Again")
                }
                TextButton(onClick = onCancelRest, modifier = Modifier.testTag(WorkoutTestTags.REST_CANCEL)) {
                    Text("Dismiss")
                }
            }
        }
    }
}

@Composable
private fun ExerciseHeader(exercise: WorkoutExerciseUi) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium)
        Text(exercise.targetSummary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SetRow(
    set: WorkoutSetUi,
    readOnly: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(WorkoutTestTags.setRow(set.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = set.isCompleted,
            onCheckedChange = if (readOnly) null else { _ -> onToggle() },
            modifier = Modifier.testTag(WorkoutTestTags.toggle(set.id)),
        )
        Text(
            text = "Set ${set.setNumber}:  ${set.weight.toPlainString()} × ${set.repetitions}  (${set.setCategory.name})",
            modifier = Modifier.weight(1f),
        )
        if (!readOnly) {
            IconButton(onClick = onEdit, modifier = Modifier.testTag(WorkoutTestTags.editSet(set.id))) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit set")
            }
            IconButton(onClick = onDelete, modifier = Modifier.testTag(WorkoutTestTags.deleteSet(set.id))) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete set")
            }
        }
    }
}

@Composable
private fun SetDialog(
    initial: WorkoutSetUi?,
    onConfirm: (BigDecimal, Int, SetCategory, BigDecimal?, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var weight by remember { mutableStateOf(initial?.weight?.toPlainString() ?: "") }
    var reps by remember { mutableStateOf(initial?.repetitions?.toString() ?: "") }
    var rpe by remember { mutableStateOf(initial?.rpe?.toPlainString() ?: "") }
    var category by remember { mutableStateOf(initial?.setCategory ?: SetCategory.WORKING) }
    var completed by remember { mutableStateOf(initial?.isCompleted ?: true) }

    val weightValue = weight.toBigDecimalOrNull()
    val repsValue = reps.toIntOrNull()
    val valid = weightValue != null && weightValue.signum() >= 0 && repsValue != null && repsValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add set" else "Edit set") },
        text = {
            Column {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.DIALOG_WEIGHT),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { input -> reps = input.filter(Char::isDigit) },
                    label = { Text("Reps") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.DIALOG_REPS),
                )
                OutlinedTextField(
                    value = rpe,
                    onValueChange = { rpe = it },
                    label = { Text("RPE (optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    SetCategory.entries.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = { Text(option.name) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = completed, onCheckedChange = { completed = it })
                    Text("Completed")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(weightValue!!, repsValue!!, category, rpe.toBigDecimalOrNull(), completed) },
                enabled = valid,
                modifier = Modifier.testTag(WorkoutTestTags.DIALOG_CONFIRM),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.myfitnesslog.feature.routine.ui.edit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

object RoutineEditTestTags {
    const val NAME_FIELD = "routine_edit_name"
    const val ADD_EXERCISE = "routine_edit_add_exercise"
    const val LIST = "routine_edit_list"
    fun moveUp(id: UUID) = "routine_edit_up_$id"
    fun moveDown(id: UUID) = "routine_edit_down_$id"
    fun edit(id: UUID) = "routine_edit_targets_$id"
    fun remove(id: UUID) = "routine_edit_remove_$id"
    const val TARGET_SETS = "target_sets"
    const val TARGET_MIN = "target_min_reps"
    const val TARGET_MAX = "target_max_reps"
    const val TARGET_REST = "target_rest"
    const val TARGET_NOTES = "target_notes"
    const val TARGET_CONFIRM = "target_confirm"
}

@Composable
fun RoutineEditScreen(
    onAddExercise: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineEditViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RoutineEditContent(
        uiState = uiState,
        onNameChange = viewModel::onNameChange,
        onAddExercise = onAddExercise,
        onMoveUp = viewModel::moveUp,
        onMoveDown = viewModel::moveDown,
        onRemove = viewModel::removeExercise,
        onUpdateTargets = viewModel::updateTargets,
        modifier = modifier,
    )
}

@Composable
fun RoutineEditContent(
    uiState: RoutineEditUiState,
    onNameChange: (String) -> Unit,
    onAddExercise: () -> Unit,
    onMoveUp: (UUID) -> Unit,
    onMoveDown: (UUID) -> Unit,
    onRemove: (UUID) -> Unit,
    onUpdateTargets: (UUID, Int, Int, Int, Int?, String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState !is RoutineEditUiState.Success) return

    var editing by remember { mutableStateOf<RoutineExerciseEditRow?>(null) }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = uiState.name,
            onValueChange = onNameChange,
            singleLine = true,
            label = { Text("Routine name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(RoutineEditTestTags.NAME_FIELD),
        )

        Button(
            onClick = onAddExercise,
            modifier = Modifier
                .padding(vertical = 8.dp)
                .testTag(RoutineEditTestTags.ADD_EXERCISE),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("Add exercise")
        }

        LazyColumn(modifier = Modifier.fillMaxSize().testTag(RoutineEditTestTags.LIST)) {
            items(uiState.exercises, key = { it.id }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.exerciseName) },
                    supportingContent = { Text(exercise.targetSummary) },
                    trailingContent = {
                        Row {
                            IconButton(
                                onClick = { onMoveUp(exercise.id) },
                                modifier = Modifier.testTag(RoutineEditTestTags.moveUp(exercise.id)),
                            ) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up") }
                            IconButton(
                                onClick = { onMoveDown(exercise.id) },
                                modifier = Modifier.testTag(RoutineEditTestTags.moveDown(exercise.id)),
                            ) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down") }
                            IconButton(
                                onClick = { editing = exercise },
                                modifier = Modifier.testTag(RoutineEditTestTags.edit(exercise.id)),
                            ) { Icon(Icons.Filled.Edit, contentDescription = "Edit targets") }
                            IconButton(
                                onClick = { onRemove(exercise.id) },
                                modifier = Modifier.testTag(RoutineEditTestTags.remove(exercise.id)),
                            ) { Icon(Icons.Filled.Delete, contentDescription = "Remove") }
                        }
                    },
                )
                HorizontalDivider()
            }
        }
    }

    editing?.let { row ->
        TargetDialog(
            row = row,
            onConfirm = { sets, min, max, rest, notes ->
                onUpdateTargets(row.id, sets, min, max, rest, notes)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun TargetDialog(
    row: RoutineExerciseEditRow,
    onConfirm: (Int, Int, Int, Int?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var sets by remember { mutableStateOf(row.targetSets.toString()) }
    var min by remember { mutableStateOf(row.minTargetReps.toString()) }
    var max by remember { mutableStateOf(row.maxTargetReps.toString()) }
    var rest by remember { mutableStateOf(row.targetRestSeconds?.toString() ?: "") }
    var notes by remember { mutableStateOf(row.notes ?: "") }

    val setsValue = sets.toIntOrNull()
    val minValue = min.toIntOrNull()
    val maxValue = max.toIntOrNull()
    val valid = setsValue != null && setsValue > 0 &&
        minValue != null && minValue > 0 &&
        maxValue != null && maxValue >= minValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.exerciseName) },
        text = {
            Column {
                NumberField("Sets", sets, RoutineEditTestTags.TARGET_SETS) { sets = it }
                NumberField("Min reps", min, RoutineEditTestTags.TARGET_MIN) { min = it }
                NumberField("Max reps", max, RoutineEditTestTags.TARGET_MAX) { max = it }
                NumberField("Rest seconds (optional)", rest, RoutineEditTestTags.TARGET_REST) { rest = it }
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth().testTag(RoutineEditTestTags.TARGET_NOTES),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        setsValue!!,
                        minValue!!,
                        maxValue!!,
                        rest.toIntOrNull(),
                        notes.ifBlank { null },
                    )
                },
                enabled = valid,
                modifier = Modifier.testTag(RoutineEditTestTags.TARGET_CONFIRM),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter(Char::isDigit)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth().testTag(testTag),
    )
}

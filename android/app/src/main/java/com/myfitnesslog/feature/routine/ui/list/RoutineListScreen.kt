package com.myfitnesslog.feature.routine.ui.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.myfitnesslog.core.ui.components.EmptyState
import java.util.UUID

object RoutineListTestTags {
    const val LIST = "routine_list"
    const val EMPTY = "routine_list_empty"
    const val FAB = "routine_create_fab"
    const val CREATE_DIALOG_FIELD = "routine_create_field"
    const val CREATE_DIALOG_CONFIRM = "routine_create_confirm"
    fun row(id: UUID) = "routine_row_$id"
    fun overflow(id: UUID) = "routine_overflow_$id"
    const val MENU_DUPLICATE = "routine_menu_duplicate"
    const val MENU_DELETE = "routine_menu_delete"
}

@Composable
fun RoutineListScreen(
    onOpenRoutine: (UUID) -> Unit,
    onEditRoutine: (UUID) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RoutineListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> onEditRoutine(event.routineId) }
    }

    RoutineListContent(
        uiState = uiState,
        onCreateRoutine = viewModel::createRoutine,
        onOpenRoutine = onOpenRoutine,
        onDuplicateRoutine = viewModel::duplicateRoutine,
        onDeleteRoutine = viewModel::deleteRoutine,
        modifier = modifier,
    )
}

@Composable
fun RoutineListContent(
    uiState: RoutineListUiState,
    onCreateRoutine: (String) -> Unit,
    onOpenRoutine: (UUID) -> Unit,
    onDuplicateRoutine: (UUID) -> Unit,
    onDeleteRoutine: (UUID) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            RoutineListUiState.Loading -> Unit
            RoutineListUiState.Empty -> EmptyState(
                icon = Icons.Filled.List,
                title = "No routines yet",
                description = "Create your first routine to start planning and tracking your workouts.",
                actionLabel = "Create routine",
                onAction = { showCreateDialog = true },
                modifier = Modifier.testTag(RoutineListTestTags.EMPTY),
            )

            is RoutineListUiState.Success -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(RoutineListTestTags.LIST),
            ) {
                items(uiState.routines, key = { it.id }) { routine ->
                    RoutineRow(
                        routine = routine,
                        onOpen = { onOpenRoutine(routine.id) },
                        onDuplicate = { onDuplicateRoutine(routine.id) },
                        onDelete = { onDeleteRoutine(routine.id) },
                    )
                    HorizontalDivider()
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag(RoutineListTestTags.FAB),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create routine")
        }
    }

    if (showCreateDialog) {
        CreateRoutineDialog(
            onConfirm = { name ->
                showCreateDialog = false
                onCreateRoutine(name)
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun RoutineRow(
    routine: RoutineListItem,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(routine.name) },
        supportingContent = {
            Text(
                text = if (routine.exerciseCount == 1) "1 exercise" else "${routine.exerciseCount} exercises",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag(RoutineListTestTags.overflow(routine.id)),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Duplicate") },
                        onClick = { menuOpen = false; onDuplicate() },
                        modifier = Modifier.testTag(RoutineListTestTags.MENU_DUPLICATE),
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = { menuOpen = false; onDelete() },
                        modifier = Modifier.testTag(RoutineListTestTags.MENU_DELETE),
                    )
                }
            }
        },
        modifier = Modifier
            .clickable(onClick = onOpen)
            .testTag(RoutineListTestTags.row(routine.id)),
    )
}

@Composable
private fun CreateRoutineDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New routine") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text("Routine name") },
                modifier = Modifier.testTag(RoutineListTestTags.CREATE_DIALOG_FIELD),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag(RoutineListTestTags.CREATE_DIALOG_CONFIRM),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.myfitnesslog.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.myfitnesslog.core.ui.components.EmptyState
import com.myfitnesslog.feature.workout.domain.RestTimerState
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID

// --- Reference-styled palette -------------------------------------------------
private val Blue = Color(0xFF2E7DF6)
private val Ink = Color(0xFF1B1F27)
private val Muted = Color(0xFF9AA0A6)
private val RowAlt = Color(0xFFF5F6F8)
private val CompletedBg = Color(0xFFDDF3C9)
private val CompletedPill = Color(0xFFB9E39A)
private val GreenCheck = Color(0xFF52B043)
private val PillGrey = Color(0xFFECEDEF)
private val Danger = Color(0xFFE3524A)

object WorkoutTestTags {
    const val LIST = "workout_list"
    const val EMPTY = "workout_empty"
    const val START_MANUAL = "workout_start_manual"
    const val ADD_EXERCISE = "workout_add_exercise"
    const val COMPLETE = "workout_complete"
    const val DISCARD = "workout_discard"
    const val DISCARD_CONFIRM = "workout_discard_confirm"
    const val DISCARD_CONFIRM_YES = "workout_discard_confirm_yes"
    const val DISCARD_CONFIRM_CANCEL = "workout_discard_confirm_cancel"
    const val ELAPSED = "workout_elapsed"
    const val REST_REMAINING = "rest_remaining"
    const val REST_SKIP = "rest_skip"
    const val REST_CANCEL = "rest_cancel"
    const val REST_RESTART = "rest_restart"
    const val REST_DONE = "rest_done"
    const val REST_MINUS = "rest_minus"
    const val REST_PLUS = "rest_plus"
    fun addSet(exerciseId: UUID) = "workout_add_set_$exerciseId"
    fun exerciseMenu(exerciseId: UUID) = "workout_exercise_menu_$exerciseId"
    fun row(rowKey: String) = "workout_row_$rowKey"
    fun previous(rowKey: String) = "workout_previous_$rowKey"
    fun weight(rowKey: String) = "workout_weight_$rowKey"
    fun reps(rowKey: String) = "workout_reps_$rowKey"
    fun rpe(rowKey: String) = "workout_rpe_$rowKey"
    fun toggle(rowKey: String) = "workout_toggle_$rowKey"
    fun delete(rowKey: String) = "workout_delete_$rowKey"
    fun moveUp(exerciseId: UUID) = "workout_move_up_$exerciseId"
    fun moveDown(exerciseId: UUID) = "workout_move_down_$exerciseId"
    fun removeExercise(exerciseId: UUID) = "workout_remove_exercise_$exerciseId"
    fun exerciseRest(exerciseId: UUID) = "workout_exercise_rest_$exerciseId"
    const val REST_PICKER = "rest_picker"
    const val REST_PICKER_LIST = "rest_picker_list"
    const val REST_PICKER_DONE = "rest_picker_done"
    fun restOption(seconds: Int) = "rest_option_$seconds"
    const val RPE_SHEET = "rpe_sheet"
    const val RPE_DONE = "rpe_done"
    const val RPE_CANCEL = "rpe_cancel"
    fun rpeValue(value: String) = "rpe_value_$value"
}

private fun Duration.human(): String {
    val total = seconds.coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}min ${s}s" else "${s}s"
}

private fun Duration.clock(): String {
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

    LaunchedEffect(viewModel) { viewModel.events.collect { onFinished(it) } }

    WorkoutContent(
        uiState = uiState,
        elapsed = elapsed,
        restState = restState,
        onStartManual = viewModel::startManualWorkout,
        onAddExercise = onAddExercise,
        onAddSet = viewModel::onAddSet,
        onToggleComplete = viewModel::onToggleComplete,
        onCommitRow = viewModel::onCommitRow,
        onRpeSelected = viewModel::onRpeSelected,
        onDeleteRow = viewModel::onDeleteRow,
        onMoveExerciseUp = viewModel::onMoveExerciseUp,
        onMoveExerciseDown = viewModel::onMoveExerciseDown,
        onRemoveExercise = viewModel::onRemoveExercise,
        onSetExerciseRest = viewModel::onSetExerciseRest,
        onComplete = viewModel::completeWorkout,
        onDiscard = viewModel::discardWorkout,
        onAdjustRest = viewModel::adjustRest,
        onSkipRest = viewModel::skipRest,
        onCancelRest = viewModel::cancelRest,
        onRestartRest = viewModel::restartRest,
        modifier = modifier,
    )
}

@Composable
fun WorkoutContent(
    uiState: WorkoutUiState,
    elapsed: Duration,
    restState: RestTimerState,
    onStartManual: () -> Unit,
    onAddExercise: (UUID) -> Unit,
    onAddSet: (UUID) -> Unit,
    onToggleComplete: (UUID, String, String, String) -> Unit,
    onCommitRow: (UUID, String, String, String) -> Unit,
    onRpeSelected: (UUID, String, BigDecimal?) -> Unit,
    onDeleteRow: (UUID, String) -> Unit,
    onMoveExerciseUp: (UUID) -> Unit,
    onMoveExerciseDown: (UUID) -> Unit,
    onRemoveExercise: (UUID) -> Unit,
    onSetExerciseRest: (UUID, Int) -> Unit,
    onComplete: () -> Unit,
    onDiscard: () -> Unit,
    onAdjustRest: (Int) -> Unit,
    onSkipRest: () -> Unit,
    onCancelRest: () -> Unit,
    onRestartRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var rpeExerciseId by rememberSaveable { mutableStateOf<String?>(null) }
    var rpeRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var restPickerExerciseId by rememberSaveable { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        when (uiState) {
            WorkoutUiState.Loading -> Unit

            WorkoutUiState.NoActiveWorkout -> EmptyState(
                icon = Icons.Filled.PlayArrow,
                title = "No active workout",
                description = "Start a fresh session and log your sets as you train.",
                actionLabel = "Start empty workout",
                onAction = onStartManual,
                actionTestTag = WorkoutTestTags.START_MANUAL,
                modifier = Modifier.testTag(WorkoutTestTags.EMPTY),
            )

            is WorkoutUiState.Active -> Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
                WorkoutTopBar(readOnly = uiState.isReadOnly, onFinish = onComplete)
                StatsHeader(uiState, elapsed)

                LazyColumn(modifier = Modifier.fillMaxSize().testTag(WorkoutTestTags.LIST)) {
                    uiState.exercises.forEach { exercise ->
                        item(key = exercise.id) {
                            ExerciseHeader(
                                exercise = exercise,
                                readOnly = uiState.isReadOnly,
                                onMoveUp = { onMoveExerciseUp(exercise.id) },
                                onMoveDown = { onMoveExerciseDown(exercise.id) },
                                onRemove = { onRemoveExercise(exercise.id) },
                                onEditRest = { restPickerExerciseId = exercise.id.toString() },
                            )
                        }
                        item(key = "cols_${exercise.id}") { SetTableHeader() }
                        items(exercise.rows, key = { it.rowKey }) { row ->
                            SetRow(
                                row = row,
                                index = exercise.rows.indexOf(row),
                                readOnly = uiState.isReadOnly,
                                onToggle = { w, r -> onToggleComplete(exercise.id, row.rowKey, w, r) },
                                onCommit = { w, r -> onCommitRow(exercise.id, row.rowKey, w, r) },
                                onOpenRpe = { rpeExerciseId = exercise.id.toString(); rpeRowKey = row.rowKey },
                                onDelete = { onDeleteRow(exercise.id, row.rowKey) },
                            )
                        }
                        if (!uiState.isReadOnly) {
                            item(key = "add_${exercise.id}") {
                                PillButton(
                                    text = "+ Add Set",
                                    onClick = { onAddSet(exercise.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                        .testTag(WorkoutTestTags.addSet(exercise.id)),
                                )
                            }
                        }
                        item(key = "gap_${exercise.id}") { Spacer(Modifier.height(12.dp)) }
                    }
                    if (!uiState.isReadOnly) {
                        item(key = "bottom_actions") { BottomActions(uiState.sessionId, onAddExercise, onDiscard) }
                    }
                    item(key = "bottom_space") { Spacer(Modifier.height(96.dp)) }
                }
            }
        }

        // Docked rest bar (overlay).
        if (uiState is WorkoutUiState.Active && !uiState.isReadOnly) {
            RestTimerBar(
                restState = restState,
                onAdjustRest = onAdjustRest,
                onSkipRest = onSkipRest,
                onCancelRest = onCancelRest,
                onRestartRest = onRestartRest,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // RPE sheet (overlay).
        val targetRow = (uiState as? WorkoutUiState.Active)
            ?.takeIf { rpeRowKey != null }
            ?.exercises?.flatMap { it.rows }?.firstOrNull { it.rowKey == rpeRowKey }
        if (targetRow != null && rpeExerciseId != null) {
            RpeSheet(
                row = targetRow,
                onSelect = { rpe ->
                    onRpeSelected(UUID.fromString(rpeExerciseId!!), targetRow.rowKey, rpe)
                    rpeRowKey = null; rpeExerciseId = null
                },
                onDismiss = { rpeRowKey = null; rpeExerciseId = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        // Rest-timer wheel picker (overlay).
        val restExercise = (uiState as? WorkoutUiState.Active)
            ?.takeIf { restPickerExerciseId != null }
            ?.exercises?.firstOrNull { it.id.toString() == restPickerExerciseId }
        if (restExercise != null) {
            RestPickerSheet(
                exerciseName = restExercise.exerciseName,
                initialSeconds = restExercise.restSeconds,
                onConfirm = { seconds ->
                    onSetExerciseRest(restExercise.id, seconds)
                    restPickerExerciseId = null
                },
                onDismiss = { restPickerExerciseId = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun WorkoutTopBar(readOnly: Boolean, onFinish: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Ink)
        Spacer(Modifier.width(4.dp))
        Text("Log Workout", style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        if (!readOnly) {
            Button(
                onClick = onFinish,
                colors = ButtonDefaults.buttonColors(containerColor = Blue),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                modifier = Modifier.testTag(WorkoutTestTags.COMPLETE),
            ) { Text("Finish") }
        }
    }
}

@Composable
private fun StatsHeader(state: WorkoutUiState.Active, elapsed: Duration) {
    val completed = state.exercises.flatMap { it.rows }.filter { it.isCompleted }
    val volume = completed.fold(BigDecimal.ZERO) { acc, r ->
        acc + (r.weight ?: BigDecimal.ZERO) * BigDecimal(r.repetitions ?: 0)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        StatCell("Duration", elapsed.human(), valueColor = Blue, tag = WorkoutTestTags.ELAPSED)
        StatCell("Volume", "${volume.stripTrailingZeros().toPlainString()} kg")
        StatCell("Sets", "${completed.size}")
    }
    androidx.compose.material3.HorizontalDivider(color = RowAlt)
}

@Composable
private fun StatCell(label: String, value: String, valueColor: Color = Ink, tag: String? = null) {
    Column {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(
            value,
            color = valueColor,
            fontWeight = FontWeight.SemiBold,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier,
        )
    }
}

@Composable
private fun ExerciseHeader(
    exercise: WorkoutExerciseUi,
    readOnly: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEditRest: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(RowAlt))
            Spacer(Modifier.width(12.dp))
            Text(exercise.exerciseName, color = Blue, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!readOnly) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.testTag(WorkoutTestTags.exerciseMenu(exercise.id))) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Exercise menu", tint = Ink)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Move up") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) },
                            onClick = { menuOpen = false; onMoveUp() },
                            modifier = Modifier.testTag(WorkoutTestTags.moveUp(exercise.id)),
                        )
                        DropdownMenuItem(
                            text = { Text("Move down") },
                            leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) },
                            onClick = { menuOpen = false; onMoveDown() },
                            modifier = Modifier.testTag(WorkoutTestTags.moveDown(exercise.id)),
                        )
                        DropdownMenuItem(
                            text = { Text("Remove exercise", color = Danger) },
                            onClick = { menuOpen = false; onRemove() },
                            modifier = Modifier.testTag(WorkoutTestTags.removeExercise(exercise.id)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Add notes here…", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "⏱  Rest Timer: ${formatRest(exercise.restSeconds)}",
            color = Blue,
            fontSize = 13.sp,
            modifier = Modifier
                .then(if (readOnly) Modifier else Modifier.clickable { onEditRest() })
                .testTag(WorkoutTestTags.exerciseRest(exercise.id)),
        )
    }
}

/**
 * Selectable rest durations (spec §5): **Off**, then 5s steps up to 2min, then 15s
 * steps up to 5min. Matches the wheel picker the user configures per exercise.
 */
internal val RestOptions: List<Int> = buildList {
    add(0) // Off
    var s = 5
    while (s <= 120) { add(s); s += 5 }
    s = 135
    while (s <= 300) { add(s); s += 15 }
}

/** "Off" / "45s" / "1min 15s". */
internal fun formatRest(seconds: Int): String = when {
    seconds <= 0 -> "Off"
    seconds < 60 -> "${seconds}s"
    else -> "${seconds / 60}min ${seconds % 60}s"
}

/**
 * Bottom-sheet wheel picker for an exercise's rest duration. The centered row is the
 * selection; scrolling snaps to whole options. Confirm persists via [onConfirm].
 */
@Composable
private fun RestPickerSheet(
    exerciseName: String,
    initialSeconds: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemHeight = 56.dp
    val initialIndex = RestOptions.indexOfFirst { it >= initialSeconds }
        .let { if (it < 0) RestOptions.lastIndex else it }
    val listState = rememberLazyListState()
    // Seed the selection so Done works even before the list is scrolled/measured.
    var selectedIndex by remember { mutableStateOf(initialIndex) }

    LaunchedEffect(Unit) { listState.scrollToItem(initialIndex) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) {
                null
            } else {
                val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                info.visibleItemsInfo.minByOrNull { kotlin.math.abs((it.offset + it.size / 2f) - center) }?.index
            }
        }.collect { lazyIndex ->
            // Index 0 is the top spacer; real options start at 1.
            if (lazyIndex != null) selectedIndex = (lazyIndex - 1).coerceIn(0, RestOptions.lastIndex)
        }
    }

    Surface(
        color = Color.White,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth().testTag(WorkoutTestTags.REST_PICKER),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(RowAlt))
            Text("Rest Timer", fontWeight = FontWeight.SemiBold, color = Ink)
            Text(exerciseName, color = Muted, fontSize = 13.sp)
            androidx.compose.material3.HorizontalDivider(color = RowAlt)

            Box(
                modifier = Modifier.fillMaxWidth().height(itemHeight * 3),
                contentAlignment = Alignment.Center,
            ) {
                // Fixed selection frame in the middle.
                Box(
                    Modifier.fillMaxWidth().height(itemHeight)
                        .clip(RoundedCornerShape(12.dp)).background(RowAlt),
                )
                LazyColumn(
                    state = listState,
                    flingBehavior = rememberSnapFlingBehavior(listState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.REST_PICKER_LIST),
                ) {
                    item { Spacer(Modifier.height(itemHeight)) }
                    itemsIndexed(RestOptions) { index, secs ->
                        val isSelected = index == selectedIndex
                        Box(
                            modifier = Modifier.fillMaxWidth().height(itemHeight)
                                .clickable { selectedIndex = index }
                                .testTag(WorkoutTestTags.restOption(secs)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                formatRest(secs),
                                color = if (isSelected) Ink else Muted,
                                fontSize = if (isSelected) 26.sp else 18.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    item { Spacer(Modifier.height(itemHeight)) }
                }
            }

            Box(
                modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                    .background(Blue).clickable { onConfirm(RestOptions[selectedIndex]) }
                    .testTag(WorkoutTestTags.REST_PICKER_DONE),
                contentAlignment = Alignment.Center,
            ) { Text("Done", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onDismiss() },
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", color = Muted) }
        }
    }
}

@Composable
private fun SetTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCell("SET", 0.7f)
        HeaderCell("PREVIOUS", 1.7f)
        HeaderCell("KG", 1f)
        HeaderCell("REPS", 1f)
        HeaderCell("RPE", 1f)
        HeaderCell("✓", 0.7f)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        color = Muted,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(weight),
    )
}

@Composable
private fun SetRow(
    row: WorkoutSetRowUi,
    index: Int,
    readOnly: Boolean,
    onToggle: (String, String) -> Unit,
    onCommit: (String, String) -> Unit,
    onOpenRpe: () -> Unit,
    onDelete: () -> Unit,
) {
    var weightText by rememberSaveable(row.rowKey) { mutableStateOf(row.weight?.toPlainString() ?: "") }
    var repsText by rememberSaveable(row.rowKey) { mutableStateOf(row.repetitions?.toString() ?: "") }
    val focusManager = LocalFocusManager.current

    val bg = when {
        row.isCompleted -> CompletedBg
        index % 2 == 1 -> RowAlt
        else -> Color.White
    }

    Row(
        modifier = Modifier.fillMaxWidth().background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(WorkoutTestTags.row(row.rowKey)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${row.setNumber}",
            color = Ink,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(0.7f),
        )
        Text(
            text = row.previous ?: "-",
            color = Muted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1.7f).testTag(WorkoutTestTags.previous(row.rowKey)),
        )
        NumberCell(
            text = weightText,
            placeholder = "0",
            completed = row.isCompleted,
            readOnly = readOnly,
            onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
            onCommit = { onCommit(weightText, repsText) },
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f).testTag(WorkoutTestTags.weight(row.rowKey)),
        )
        NumberCell(
            text = repsText,
            placeholder = "0",
            completed = row.isCompleted,
            readOnly = readOnly,
            onValueChange = { repsText = it.filter(Char::isDigit) },
            onCommit = { onCommit(weightText, repsText) },
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f).testTag(WorkoutTestTags.reps(row.rowKey)),
        )
        // RPE pill
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val hasRpe = row.rpe != null
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (hasRpe && row.isCompleted) CompletedPill else PillGrey)
                    .then(
                        if (readOnly) Modifier
                        // Clear field focus first so the soft keyboard closes as the RPE
                        // sheet opens (otherwise both are shown at once).
                        else Modifier.clickable { focusManager.clearFocus(); onCommit(weightText, repsText); onOpenRpe() },
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag(WorkoutTestTags.rpe(row.rowKey)),
            ) {
                Text(row.rpe?.toPlainString() ?: "RPE", color = if (hasRpe) Ink else Muted, fontSize = 13.sp)
            }
        }
        // Completion checkbox (rounded square)
        Box(modifier = Modifier.weight(0.7f), contentAlignment = Alignment.Center) {
            CompletionBox(
                checked = row.isCompleted,
                enabled = !readOnly,
                onToggle = { onToggle(weightText, repsText) },
                modifier = Modifier.testTag(WorkoutTestTags.toggle(row.rowKey)),
            )
        }
    }
}

@Composable
private fun NumberCell(
    text: String,
    placeholder: String,
    completed: Boolean,
    readOnly: Boolean,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = text,
        onValueChange = onValueChange,
        enabled = !readOnly,
        singleLine = true,
        textStyle = TextStyle(
            textAlign = TextAlign.Center,
            color = if (text.isEmpty()) Muted else Ink,
            fontWeight = if (completed) FontWeight.Bold else FontWeight.Normal,
            fontSize = 16.sp,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.onFocusChanged { if (!it.isFocused) onCommit() },
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.Center) {
                if (text.isEmpty()) Text(placeholder, color = Muted, fontSize = 16.sp)
                inner()
            }
        },
    )
}

@Composable
private fun CompletionBox(checked: Boolean, enabled: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (checked) GreenCheck else PillGrey)
            .then(if (enabled) Modifier.clickable { onToggle() } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = if (checked) "Completed" else "Mark complete",
            tint = if (checked) Color.White else Muted,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(RowAlt)
            .clickable { onClick() }.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Ink, fontWeight = FontWeight.Medium) }
}

@Composable
private fun BottomActions(sessionId: UUID, onAddExercise: (UUID) -> Unit, onDiscard: () -> Unit) {
    var confirmDiscard by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onClick = { onAddExercise(sessionId) },
            colors = ButtonDefaults.buttonColors(containerColor = Blue),
            modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.ADD_EXERCISE),
        ) { Text("+ Add Exercise") }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(RowAlt)
                    .clickable { }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Settings", color = Ink) }
            Box(
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(RowAlt)
                    .clickable { confirmDiscard = true }.padding(vertical = 12.dp)
                    .testTag(WorkoutTestTags.DISCARD),
                contentAlignment = Alignment.Center,
            ) { Text("Discard Workout", color = Danger) }
        }
    }
    if (confirmDiscard) {
        DiscardWorkoutDialog(
            onConfirm = { confirmDiscard = false; onDiscard() },
            onDismiss = { confirmDiscard = false },
        )
    }
}

@Composable
private fun RestTimerBar(
    restState: RestTimerState,
    onAdjustRest: (Int) -> Unit,
    onSkipRest: () -> Unit,
    onCancelRest: () -> Unit,
    onRestartRest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (restState is RestTimerState.Idle) return

    Surface(color = Color.White, shadowElevation = 8.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (restState) {
                RestTimerState.Idle -> Unit
                is RestTimerState.Running -> {
                    RestChip("-15", onClick = { onAdjustRest(-15) }, tag = WorkoutTestTags.REST_MINUS)
                    Text(
                        Duration.ofSeconds(restState.remainingSeconds.toLong()).clock(),
                        modifier = Modifier.weight(1f).testTag(WorkoutTestTags.REST_REMAINING),
                        textAlign = TextAlign.Center,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Ink,
                    )
                    RestChip("+15", onClick = { onAdjustRest(15) }, tag = WorkoutTestTags.REST_PLUS)
                    Button(
                        onClick = onSkipRest,
                        colors = ButtonDefaults.buttonColors(containerColor = Blue),
                        modifier = Modifier.testTag(WorkoutTestTags.REST_SKIP),
                    ) { Text("Skip") }
                }
                RestTimerState.Finished -> {
                    Text("Rest done", modifier = Modifier.weight(1f).testTag(WorkoutTestTags.REST_DONE), color = Ink)
                    TextButton(onClick = onRestartRest, modifier = Modifier.testTag(WorkoutTestTags.REST_RESTART)) { Text("Again") }
                    TextButton(onClick = onCancelRest, modifier = Modifier.testTag(WorkoutTestTags.REST_CANCEL)) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
private fun RestChip(text: String, onClick: () -> Unit, tag: String) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(RowAlt).clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = Ink, fontWeight = FontWeight.Medium) }
}

@Composable
private fun RpeSheet(
    row: WorkoutSetRowUi,
    onSelect: (BigDecimal?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selected by rememberSaveable(row.rowKey) { mutableStateOf(row.rpe?.toPlainString()) }
    val selectedValue = selected?.let(::BigDecimal)
    val descriptor = selectedValue?.let(RpeScale::describe)

    Surface(
        color = Color.White,
        shadowElevation = 16.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = modifier.fillMaxWidth().testTag(WorkoutTestTags.RPE_SHEET),
    ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(RowAlt))
                Text("Log Set RPE", fontWeight = FontWeight.SemiBold, color = Ink)
                Text(
                    "Set ${row.setNumber}: ${row.weight?.toPlainString() ?: "-"}kg x ${row.repetitions ?: "-"} reps",
                    color = Muted, fontSize = 13.sp,
                )
                Text(selected ?: "—", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Ink)
                Text(descriptor?.label ?: " ", fontWeight = FontWeight.Medium, color = Ink, fontSize = 14.sp)
                Text(descriptor?.hint ?: " ", color = Muted, fontSize = 12.sp)
                // Pill selector
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(RowAlt).padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RpeScale.values.forEach { value ->
                        val text = value.toPlainString()
                        val isSel = selected == text
                        Box(
                            modifier = Modifier.weight(1f).clip(CircleShape)
                                .background(if (isSel) Blue else Color.Transparent)
                                .clickable { selected = text }
                                .padding(vertical = 8.dp)
                                .testTag(WorkoutTestTags.rpeValue(text)),
                            contentAlignment = Alignment.Center,
                        ) { Text(text, color = if (isSel) Color.White else Ink, fontWeight = FontWeight.Medium, fontSize = 12.sp) }
                    }
                }
                Box(
                    modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp))
                        .background(GreenCheck).clickable { onSelect(selected?.let(::BigDecimal)) }
                        .testTag(WorkoutTestTags.RPE_DONE),
                    contentAlignment = Alignment.Center,
                ) { Text("Done ✓", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onDismiss() }
                        .testTag(WorkoutTestTags.RPE_CANCEL),
                    contentAlignment = Alignment.Center,
                ) { Text("Cancel", color = Muted) }
            }
        }
}

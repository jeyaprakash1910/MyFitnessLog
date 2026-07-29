package com.myfitnesslog.feature.workout.ui.indicator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfitnesslog.feature.workout.ui.DiscardWorkoutDialog
import java.time.Duration

object WorkoutIndicatorTestTags {
    const val PILL = "workout_indicator"
    const val ELAPSED = "workout_indicator_elapsed"
    const val ROUTINE = "workout_indicator_routine"
    const val EXERCISE = "workout_indicator_exercise"
    const val RESUME = "workout_indicator_resume"
    const val DISCARD = "workout_indicator_discard"
}

private val LiveGreen = Color(0xFF3DDC84)
private val Danger = Color(0xFFE3524A)

private fun Duration.asHuman(): String {
    val total = seconds.coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return if (m > 0) "${m}min ${s}s" else "${s}s"
}

/**
 * Persistent workout indicator (Milestone G). Shown while a workout is active on the
 * other top-level screens. Tapping it (or the chevron) resumes the workout; the bin
 * icon discards it behind a confirmation. Reflects live state: routine name, elapsed
 * time, the current exercise, and a "live" dot.
 */
@Composable
fun WorkoutIndicator(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WorkoutIndicatorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    WorkoutIndicatorContent(
        state = state,
        onClick = onClick,
        onDiscard = viewModel::discardActiveWorkout,
        modifier = modifier,
    )
}

@Composable
fun WorkoutIndicatorContent(
    state: WorkoutIndicatorUiState,
    onClick: () -> Unit,
    onDiscard: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state !is WorkoutIndicatorUiState.Visible) return
    var confirmDiscard by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(28.dp),
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag(WorkoutIndicatorTestTags.PILL),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Resume (expand) affordance.
            CircleButton(
                icon = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Resume workout",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                onClick = onClick,
                modifier = Modifier.testTag(WorkoutIndicatorTestTags.RESUME),
            )

            Column(
                modifier = Modifier.weight(1f).clickable { onClick() },
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.size(9.dp).clip(CircleShape).background(LiveGreen))
                    Text(
                        state.routineName,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        modifier = Modifier.testTag(WorkoutIndicatorTestTags.ROUTINE),
                    )
                    Text(
                        state.elapsed.asHuman(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 15.sp,
                        modifier = Modifier.testTag(WorkoutIndicatorTestTags.ELAPSED),
                    )
                }
                if (state.currentExercise != null) {
                    Text(
                        state.currentExercise,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        modifier = Modifier.testTag(WorkoutIndicatorTestTags.EXERCISE),
                    )
                }
            }

            // Discard (destructive) — confirmed before it acts.
            CircleButton(
                icon = Icons.Filled.Delete,
                contentDescription = "Discard workout",
                tint = Danger,
                onClick = { confirmDiscard = true },
                modifier = Modifier.testTag(WorkoutIndicatorTestTags.DISCARD),
            )
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
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint)
    }
}

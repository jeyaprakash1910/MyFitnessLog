package com.myfitnesslog.feature.workout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Destructive-action red, matching the app's "Discard Workout" accent. */
private val Danger = Color(0xFFE3524A)

/**
 * Confirmation before discarding a workout — an irreversible action (discard
 * tombstones the session and cannot be undone). Shared by the workout screen's
 * "Discard Workout" button and the persistent indicator's bin icon. Theme-aware so
 * it reads correctly in light and dark; full-width stacked buttons with the
 * destructive action first, then Cancel.
 */
@Composable
fun DiscardWorkoutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().testTag(WorkoutTestTags.DISCARD_CONFIRM),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    "Are you sure you want to discard this workout?",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                )
                DialogButton(
                    text = "Discard Workout",
                    textColor = Danger,
                    tag = WorkoutTestTags.DISCARD_CONFIRM_YES,
                    onClick = onConfirm,
                )
                DialogButton(
                    text = "Cancel",
                    textColor = MaterialTheme.colorScheme.onSurface,
                    tag = WorkoutTestTags.DISCARD_CONFIRM_CANCEL,
                    onClick = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun DialogButton(text: String, textColor: Color, tag: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick() }.padding(vertical = 14.dp).testTag(tag),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = textColor, fontWeight = FontWeight.Medium) }
}

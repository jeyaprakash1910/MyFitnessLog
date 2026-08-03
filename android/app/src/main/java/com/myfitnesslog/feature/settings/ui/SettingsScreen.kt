package com.myfitnesslog.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfitnesslog.core.ui.theme.MyFitnessLogTheme
import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues

/** Test tags used by Compose UI tests to locate elements. */
object SettingsTestTags {
    const val SCREEN = "settings_screen"
    fun previousOption(value: PreviousWorkoutValues) = "settings_previous_${value.name}"
}

/**
 * Route composable: pulls the Hilt ViewModel, collects its state lifecycle-aware,
 * and delegates to the stateless [SettingsContent].
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onPreviousWorkoutValuesSelected = viewModel::onPreviousWorkoutValuesSelected,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    onPreviousWorkoutValuesSelected: (PreviousWorkoutValues) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag(SettingsTestTags.SCREEN)) {
        SettingsSectionHeader(title = "Previous Workout Values")
        Text(
            text = "Where the Previous column pulls weight, reps and RPE from during a workout.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.selectableGroup()) {
                PreviousWorkoutValueOption(
                    title = "Any Workout",
                    subtitle = "Use the last time you did the exercise, in any routine.",
                    selected = uiState.previousWorkoutValues == PreviousWorkoutValues.ANY_WORKOUT,
                    onSelect = { onPreviousWorkoutValuesSelected(PreviousWorkoutValues.ANY_WORKOUT) },
                    testTag = SettingsTestTags.previousOption(PreviousWorkoutValues.ANY_WORKOUT),
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PreviousWorkoutValueOption(
                    title = "Same Routine",
                    subtitle = "Use the last time you did the exercise in this routine.",
                    selected = uiState.previousWorkoutValues == PreviousWorkoutValues.SAME_ROUTINE,
                    onSelect = { onPreviousWorkoutValuesSelected(PreviousWorkoutValues.SAME_ROUTINE) },
                    testTag = SettingsTestTags.previousOption(PreviousWorkoutValues.SAME_ROUTINE),
                )
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun PreviousWorkoutValueOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
    testTag: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(testTag)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                RadioButton(selected = selected, onClick = null)
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 52.dp),
            )
        }
    }
}

@Preview
@Composable
private fun SettingsContentPreview() {
    MyFitnessLogTheme {
        SettingsContent(
            uiState = SettingsUiState(previousWorkoutValues = PreviousWorkoutValues.ANY_WORKOUT),
            onPreviousWorkoutValuesSelected = {},
        )
    }
}

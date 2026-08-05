package com.myfitnesslog.feature.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfitnesslog.BuildConfig
import com.myfitnesslog.core.ui.theme.MyFitnessLogTheme
import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues
import com.myfitnesslog.feature.update.domain.UpdateStatus
import com.myfitnesslog.feature.update.ui.UpdateViewModel

/** Test tags used by Compose UI tests to locate elements. */
object SettingsTestTags {
    const val SCREEN = "settings_screen"
    const val APP_VERSION = "settings_app_version"
    fun previousOption(value: PreviousWorkoutValues) = "settings_previous_${value.name}"
}

/**
 * Route composable: pulls the Hilt ViewModel, collects its state lifecycle-aware,
 * and delegates to the stateless [SettingsContent].
 *
 * The update status comes from a second ViewModel rather than being folded into
 * [SettingsViewModel]: it is owned by a singleton repository shared with the update
 * banner, and routing it through the settings ViewModel would make one feature's
 * state a dependency of an unrelated one for no gain.
 */
@Composable
fun SettingsScreen(
    onOpenAppUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { updateViewModel.checkOnce() }

    SettingsContent(
        uiState = uiState,
        updateStatus = updateState.status,
        onPreviousWorkoutValuesSelected = viewModel::onPreviousWorkoutValuesSelected,
        onOpenAppUpdate = onOpenAppUpdate,
        modifier = modifier,
    )
}

@Composable
private fun SettingsContent(
    uiState: SettingsUiState,
    updateStatus: UpdateStatus,
    onPreviousWorkoutValuesSelected: (PreviousWorkoutValues) -> Unit,
    onOpenAppUpdate: () -> Unit,
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

        SettingsSectionHeader(title = "About")
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            AppVersionRow(status = updateStatus, onClick = onOpenAppUpdate)
        }
    }
}

/**
 * The manual way in to the update screen, and the answer to "what am I running?".
 *
 * Always tappable, including while a check is in flight or has failed: the point of
 * the row is to reach a screen that can retry, so disabling it in exactly the states
 * where the user wants to retry would be backwards.
 */
@Composable
private fun AppVersionRow(status: UpdateStatus, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(SettingsTestTags.APP_VERSION)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = "App version", style = MaterialTheme.typography.bodyLarge)
            Text(
                text = when (status) {
                    is UpdateStatus.Available ->
                        "${BuildConfig.VERSION_NAME} \u00B7 update to ${status.latestVersion}"
                    is UpdateStatus.UpToDate -> "${status.installedVersion} \u00B7 up to date"
                    // Fall back to the build constant rather than showing nothing:
                    // the installed version is known locally even when the check is
                    // not, and it is the more useful half of this row.
                    is UpdateStatus.Unavailable -> "${BuildConfig.VERSION_NAME} \u00B7 ${status.reason}"
                    UpdateStatus.Checking, UpdateStatus.Unknown ->
                        "${BuildConfig.VERSION_NAME} \u00B7 checking\u2026"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (status is UpdateStatus.Available) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
            updateStatus = UpdateStatus.Available(
                installedVersion = "1.1.0",
                latestVersion = "1.2.0",
                releaseNotes = "",
                sizeBytes = 24_998_400,
            ),
            onPreviousWorkoutValuesSelected = {},
            onOpenAppUpdate = {},
        )
    }
}

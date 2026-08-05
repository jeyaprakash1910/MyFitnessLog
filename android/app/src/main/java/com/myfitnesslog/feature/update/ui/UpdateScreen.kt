package com.myfitnesslog.feature.update.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfitnesslog.core.ui.theme.MyFitnessLogTheme
import com.myfitnesslog.feature.update.domain.DownloadStatus
import com.myfitnesslog.feature.update.domain.UpdateStatus

/** Test tags used by Compose UI tests to locate elements. */
object UpdateTestTags {
    const val SCREEN = "update_screen"
    const val HEADLINE = "update_headline"
    const val INSTALL_BUTTON = "update_install_button"
    const val CHECK_BUTTON = "update_check_button"
    const val PROGRESS = "update_progress"
    const val RELEASE_NOTES = "update_release_notes"
}

/**
 * Route composable: pulls the Hilt ViewModel, collects its state lifecycle-aware,
 * and delegates to the stateless [UpdateContent].
 */
@Composable
fun UpdateScreen(
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Opening the screen is the user asking the question, so check on arrival -
    // but only if no check has already answered it this launch.
    LaunchedEffect(Unit) { viewModel.checkOnce() }

    UpdateContent(
        uiState = uiState,
        onInstall = { viewModel.downloadAndInstall(context) },
        onRecheck = viewModel::recheck,
        onDismissDownload = viewModel::dismissDownload,
        modifier = modifier,
    )
}

@Composable
private fun UpdateContent(
    uiState: UpdateUiState,
    onInstall: () -> Unit,
    onRecheck: () -> Unit,
    onDismissDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag(UpdateTestTags.SCREEN),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val status = uiState.status) {
            is UpdateStatus.Available -> AvailableState(
                status = status,
                download = uiState.download,
                onInstall = onInstall,
                onDismissDownload = onDismissDownload,
            )

            is UpdateStatus.UpToDate -> SimpleState(
                icon = Icons.Filled.CheckCircle,
                tint = MaterialTheme.colorScheme.primary,
                headline = "You're up to date",
                detail = "Version ${status.installedVersion} is the latest release.",
                onRecheck = onRecheck,
            )

            is UpdateStatus.Unavailable -> SimpleState(
                icon = Icons.Filled.Info,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                headline = "Can't check right now",
                detail = status.reason,
                onRecheck = onRecheck,
            )

            UpdateStatus.Checking, UpdateStatus.Unknown -> CheckingState()
        }
    }
}

@Composable
private fun AvailableState(
    status: UpdateStatus.Available,
    download: DownloadStatus,
    onInstall: () -> Unit,
    onDismissDownload: () -> Unit,
) {
    IconBadge(Icons.Filled.Info, MaterialTheme.colorScheme.primary)

    Text(
        text = "Version ${status.latestVersion} is available",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .testTag(UpdateTestTags.HEADLINE),
    )
    Text(
        text = buildString {
            append("You have ${status.installedVersion}.")
            if (status.sizeBytes > 0) append(" Download is ${formatSize(status.sizeBytes)}.")
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )

    if (status.releaseNotes.isNotBlank()) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    text = "What's new",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    // Rendered as plain text: the notes are Markdown, but pulling in
                    // a Markdown renderer to italicise a changelog is not a trade
                    // worth making. Plain text is readable and never mis-renders.
                    text = status.releaseNotes.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag(UpdateTestTags.RELEASE_NOTES),
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    when (download) {
        is DownloadStatus.Downloading -> DownloadProgress(download.fraction)

        DownloadStatus.ReadyToInstall -> {
            Text(
                text = "Downloaded. Follow the system prompt to finish installing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            // The system prompt can be dismissed without installing, so the action
            // stays available rather than assuming success.
            OutlinedButton(
                onClick = onDismissDownload,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) { Text("Install again") }
        }

        is DownloadStatus.Failed -> {
            Text(
                text = download.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(12.dp))
            InstallButton(label = "Try again", onClick = onInstall)
        }

        DownloadStatus.Idle -> InstallButton(label = "Download and install", onClick = onInstall)
    }

    Spacer(Modifier.height(32.dp))
}

@Composable
private fun InstallButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag(UpdateTestTags.INSTALL_BUTTON),
    ) { Text(label) }
}

@Composable
private fun DownloadProgress(fraction: Float?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag(UpdateTestTags.PROGRESS),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Indeterminate until the total size is known, rather than a bar sitting at
        // zero, which reads as "stuck" instead of "starting".
        if (fraction == null) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (fraction == null) "Downloading…"
            else "Downloading… ${(fraction * 100).toInt()}%",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SimpleState(
    icon: ImageVector,
    tint: Color,
    headline: String,
    detail: String,
    onRecheck: () -> Unit,
) {
    IconBadge(icon, tint)
    Text(
        text = headline,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .testTag(UpdateTestTags.HEADLINE),
    )
    Text(
        text = detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    Spacer(Modifier.height(16.dp))
    OutlinedButton(
        onClick = onRecheck,
        modifier = Modifier.testTag(UpdateTestTags.CHECK_BUTTON),
    ) {
        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Check again", modifier = Modifier.padding(start = 8.dp))
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun CheckingState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Checking for updates…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(UpdateTestTags.HEADLINE),
        )
    }
}

/** Matches the tinted circular badge the shared empty state uses. */
@Composable
private fun IconBadge(icon: ImageVector, tint: Color) {
    Column(
        modifier = Modifier.padding(top = 40.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(20.dp)
                .size(40.dp),
        )
    }
}

/**
 * Formats a byte count for display. Whole MB below 10, one decimal above, so a
 * download reads as "24 MB" rather than "24.0 MB" or "23.84 MB".
 */
private fun formatSize(bytes: Long): String {
    val megabytes = bytes / 1_048_576.0
    return if (megabytes >= 10) "${megabytes.toInt()} MB"
    else String.format("%.1f MB", megabytes)
}

@Preview
@Composable
private fun UpdateAvailablePreview() {
    MyFitnessLogTheme {
        UpdateContent(
            uiState = UpdateUiState(
                status = UpdateStatus.Available(
                    installedVersion = "1.1.0",
                    latestVersion = "1.2.0",
                    releaseNotes = "Editable per-exercise notes during a workout.\nRPE is kept when completing a set via the checkbox.",
                    sizeBytes = 24_998_400,
                ),
            ),
            onInstall = {},
            onRecheck = {},
            onDismissDownload = {},
        )
    }
}

@Preview
@Composable
private fun UpToDatePreview() {
    MyFitnessLogTheme {
        UpdateContent(
            uiState = UpdateUiState(status = UpdateStatus.UpToDate("1.2.0")),
            onInstall = {},
            onRecheck = {},
            onDismissDownload = {},
        )
    }
}

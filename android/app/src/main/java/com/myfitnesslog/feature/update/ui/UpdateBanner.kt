package com.myfitnesslog.feature.update.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.myfitnesslog.core.ui.theme.MyFitnessLogTheme
import com.myfitnesslog.feature.update.domain.UpdateStatus

/** Test tags used by Compose UI tests to locate elements. */
object UpdateBannerTestTags {
    const val BANNER = "update_banner"
    const val DISMISS = "update_banner_dismiss"
}

/**
 * A slim, dismissible bar shown above the app's content when a newer build exists.
 *
 * This is how the user finds out at all. A settings row alone would not do it -
 * nobody opens Settings to look for an update they don't know about - and a launch
 * dialog is worse, because it can land in the middle of a set. A banner is
 * noticeable, ignorable, and never blocks anything.
 *
 * Dismissal lasts for the process, not forever: the reminder returning on the next
 * launch is the point, and persisting "don't ask again" would let a user
 * permanently opt out of updates by reflex.
 *
 * Renders nothing in every other state, including [UpdateStatus.Unavailable] - a
 * failed check is not news.
 */
@Composable
fun UpdateBanner(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The one automatic check per launch. It lives here rather than in the
    // Application class so it happens only once the UI is actually up, and it is
    // cheap enough to be invisible: one small GET, results shared with the
    // Settings screen through the singleton repository.
    LaunchedEffect(Unit) { viewModel.checkOnce() }

    var dismissed by remember { mutableStateOf(false) }
    val status = uiState.status
    if (dismissed || status !is UpdateStatus.Available) return

    UpdateBannerContent(
        latestVersion = status.latestVersion,
        onClick = onClick,
        onDismiss = { dismissed = true },
        modifier = modifier,
    )
}

@Composable
private fun UpdateBannerContent(
    latestVersion: String,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.fillMaxWidth().testTag(UpdateBannerTestTags.BANNER),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Version $latestVersion is available",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Tap to see what's new and install it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(UpdateBannerTestTags.DISMISS),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Dismiss update notice",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Preview
@Composable
private fun UpdateBannerContentPreview() {
    MyFitnessLogTheme {
        UpdateBannerContent(latestVersion = "1.2.0", onClick = {}, onDismiss = {})
    }
}

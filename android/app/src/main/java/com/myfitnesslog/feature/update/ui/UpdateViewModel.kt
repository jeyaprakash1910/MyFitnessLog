package com.myfitnesslog.feature.update.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.update.data.AppUpdateRepository
import com.myfitnesslog.feature.update.domain.DownloadStatus
import com.myfitnesslog.feature.update.domain.UpdateStatus
import com.myfitnesslog.feature.update.install.ApkInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Immutable state for the update screen and the update banner. */
data class UpdateUiState(
    val status: UpdateStatus = UpdateStatus.Unknown,
    val download: DownloadStatus = DownloadStatus.Idle,
)

/**
 * ViewModel for the update screen.
 *
 * Thin over [AppUpdateRepository], which owns the state - so navigating away and
 * back does not restart a check or lose a download in progress. It does not check
 * on init: [checkOnce] is called by the screen, which keeps "look for an update"
 * an observable action rather than a side effect of constructing a ViewModel.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: AppUpdateRepository,
    private val installer: ApkInstaller,
) : ViewModel() {

    val uiState: StateFlow<UpdateUiState> =
        combine(repository.status, repository.downloadStatus, ::UpdateUiState)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UpdateUiState(),
            )

    /** Checks unless a check has already succeeded this launch. */
    fun checkOnce() {
        viewModelScope.launch { repository.check(force = false) }
    }

    /** Re-checks on explicit user request, ignoring the cached answer. */
    fun recheck() {
        viewModelScope.launch { repository.check(force = true) }
    }

    /**
     * Downloads the update and hands it to the system installer.
     *
     * If the app has not been allowed to request installs, the user is sent to that
     * system screen instead of starting a download that could not be used at the
     * end of it.
     */
    fun downloadAndInstall(context: Context) {
        if (!installer.canRequestInstall(context)) {
            installer.openInstallPermissionSettings(context)
            return
        }
        viewModelScope.launch {
            val apk = repository.downloadApk() ?: return@launch
            installer.install(context, apk)
        }
    }

    /** Clears a finished or failed download so its result stops being shown. */
    fun dismissDownload() = repository.clearDownloadStatus()
}

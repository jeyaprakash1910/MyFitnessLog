package com.myfitnesslog.feature.update.data

import com.myfitnesslog.feature.update.domain.DownloadStatus
import com.myfitnesslog.feature.update.domain.UpdateStatus
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Knows whether a newer build of the app exists, and can fetch it.
 *
 * Deliberately a process-wide singleton exposing state as flows rather than a
 * request/response API: the answer is asked for by two unrelated surfaces (the
 * banner above every screen and the Settings screen), and neither should trigger
 * its own network call. One check per launch, observed from both.
 */
interface AppUpdateRepository {

    /** Latest known result of an update check. */
    val status: StateFlow<UpdateStatus>

    /** Progress of the APK download, if one has been started. */
    val downloadStatus: StateFlow<DownloadStatus>

    /**
     * Runs an update check, updating [status].
     *
     * Never throws: an unreachable backend is the normal case for this app (the
     * instance sleeps, the phone is off-network) and must not be able to break a
     * screen that merely wanted to display a version number.
     *
     * @param force re-check even if a check already succeeded this launch
     */
    suspend fun check(force: Boolean = false)

    /**
     * Downloads the latest APK, reporting progress through [downloadStatus].
     *
     * @return the downloaded file, or null if the download failed
     */
    suspend fun downloadApk(): File?

    /**
     * Resets [downloadStatus] back to idle, so a dismissed or completed download
     * does not keep re-showing its result.
     *
     * This does **not** delete the downloaded file. The package installer reads it
     * after control returns here, so deleting it at that point would break the very
     * install it was fetched for. Stale APKs are instead pruned at the start of the
     * next [downloadApk], which keeps at most one on disk.
     */
    fun clearDownloadStatus()
}

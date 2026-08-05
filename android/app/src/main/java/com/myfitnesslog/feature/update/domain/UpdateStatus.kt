package com.myfitnesslog.feature.update.domain

/**
 * What the app currently knows about the availability of a newer build.
 *
 * [Unavailable] is distinct from [UpToDate] on purpose. "The backend is asleep, on
 * another network, or has no update configured" is not the same claim as "you are
 * running the latest version", and conflating them would let the app tell a
 * confident lie whenever it simply could not ask.
 */
sealed interface UpdateStatus {

    /** No check has completed yet this launch. */
    data object Unknown : UpdateStatus

    /** A check is in flight. */
    data object Checking : UpdateStatus

    /** The check succeeded and this build is the latest. */
    data class UpToDate(val installedVersion: String) : UpdateStatus

    /** The check succeeded and a newer release exists. */
    data class Available(
        val installedVersion: String,
        val latestVersion: String,
        val releaseNotes: String,
        val sizeBytes: Long,
    ) : UpdateStatus

    /**
     * The check could not be completed. [reason] is user-facing and deliberately
     * short: this is shown on a settings row, not in a crash report.
     */
    data class Unavailable(val reason: String) : UpdateStatus
}

/** Progress of an APK download, once the user has asked for one. */
sealed interface DownloadStatus {

    data object Idle : DownloadStatus

    /**
     * [fraction] is null while the total size is unknown, so the UI can show an
     * indeterminate bar rather than a bar frozen at zero.
     */
    data class Downloading(val fraction: Float?) : DownloadStatus

    /** Bytes are on disk and the installer has been handed the file. */
    data object ReadyToInstall : DownloadStatus

    data class Failed(val reason: String) : DownloadStatus
}

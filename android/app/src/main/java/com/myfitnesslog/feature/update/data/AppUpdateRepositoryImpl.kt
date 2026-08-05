package com.myfitnesslog.feature.update.data

import android.content.Context
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.update.di.InstalledVersionName
import com.myfitnesslog.feature.update.data.remote.AppUpdateApi
import com.myfitnesslog.feature.update.domain.AppVersion
import com.myfitnesslog.feature.update.domain.DownloadStatus
import com.myfitnesslog.feature.update.domain.UpdateStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Default [AppUpdateRepository]: compares this build against the backend's latest
 * release and downloads the APK on request.
 *
 * Failure handling is uniform and deliberate - every network or IO problem becomes
 * [UpdateStatus.Unavailable] rather than an exception. Checking for an update is a
 * background courtesy, not something the user asked for, so it is never allowed to
 * surface as an error, let alone during a workout.
 */
@Singleton
class AppUpdateRepositoryImpl @Inject constructor(
    private val api: AppUpdateApi,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    /**
     * This build's own version name. Injected rather than read from `BuildConfig`
     * here so the comparison can be tested at chosen versions; reading the build
     * constant directly would make every test's expected outcome change the next
     * time `version.properties` is bumped.
     */
    @InstalledVersionName private val installedVersion: String,
) : AppUpdateRepository {

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Unknown)
    override val status: StateFlow<UpdateStatus> = _status.asStateFlow()

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    override val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus.asStateFlow()

    /**
     * Serialises checks. Two surfaces observe [status] and either may ask for a
     * check on first composition, so without this the app would fire two identical
     * requests on launch.
     */
    private val checkMutex = Mutex()

    override suspend fun check(force: Boolean) {
        checkMutex.withLock {
            val settled = _status.value
            val alreadyAnswered =
                settled is UpdateStatus.UpToDate || settled is UpdateStatus.Available
            if (alreadyAnswered && !force) return

            _status.value = UpdateStatus.Checking
            _status.value = runCheck()
        }
    }

    private suspend fun runCheck(): UpdateStatus = withContext(ioDispatcher) {
        val installed = AppVersion.parseOrNull(installedVersion)
            ?: return@withContext UpdateStatus.Unavailable(
                // Only reachable if version.properties is malformed, which the
                // Gradle build already rejects - so this is a guard, not a path.
                "This build has no comparable version number.",
            )

        val response = try {
            api.getLatestVersion()
        } catch (e: IOException) {
            return@withContext UpdateStatus.Unavailable("Could not reach the server.")
        } catch (e: RuntimeException) {
            // A malformed body, an unexpected content type: still just "cannot tell".
            return@withContext UpdateStatus.Unavailable("Could not read the server's reply.")
        }

        if (response.code() == SERVICE_UNAVAILABLE) {
            return@withContext UpdateStatus.Unavailable("Updates are not set up on the server.")
        }
        val body = response.body()
        if (!response.isSuccessful || body == null) {
            return@withContext UpdateStatus.Unavailable("The server could not report a version.")
        }

        val latest = AppVersion.parseOrNull(body.versionName)
            ?: return@withContext UpdateStatus.Unavailable("The server reported an unreadable version.")

        if (latest > installed) {
            UpdateStatus.Available(
                installedVersion = installedVersion,
                latestVersion = latest.toString(),
                releaseNotes = body.releaseNotes,
                sizeBytes = body.sizeBytes,
            )
        } else {
            UpdateStatus.UpToDate(installedVersion)
        }
    }

    override suspend fun downloadApk(): File? = withContext(ioDispatcher) {
        _downloadStatus.value = DownloadStatus.Downloading(fraction = null)

        val response = try {
            api.downloadApk()
        } catch (e: IOException) {
            return@withContext fail("Could not reach the server.")
        }

        val body = response.body()
        if (!response.isSuccessful || body == null) {
            body?.close()
            return@withContext fail("The server did not send the update.")
        }

        val target = try {
            body.use { writeToCache(it.byteStream(), it.contentLength()) }
        } catch (e: IOException) {
            return@withContext fail("The download did not finish.")
        }

        _downloadStatus.value = DownloadStatus.ReadyToInstall
        target
    }

    /**
     * Streams the response to a file in the app's cache, reporting progress.
     *
     * Written to `cacheDir` and exposed through a FileProvider: the package
     * installer runs in another process and cannot read this app's private files
     * directly, and cache is the honest location for a file the system is free to
     * reclaim once the install is done.
     */
    private fun writeToCache(source: java.io.InputStream, totalBytes: Long): File {
        val directory = File(context.cacheDir, DOWNLOAD_DIRECTORY).apply { mkdirs() }
        // At most one APK is ever kept; see AppUpdateRepository.clearDownloadStatus.
        directory.listFiles()?.forEach { it.delete() }

        val target = File(directory, DOWNLOAD_FILE_NAME)
        var written = 0L
        var lastReportedPercent = -1

        source.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    written += read

                    if (totalBytes > 0) {
                        // Emit only on a whole-percent change: a StateFlow update per
                        // 8 KB buffer would recompose the progress bar thousands of
                        // times for no visible difference.
                        val percent = (written * 100 / totalBytes).toInt()
                        if (percent != lastReportedPercent) {
                            lastReportedPercent = percent
                            _downloadStatus.value = DownloadStatus.Downloading(percent / 100f)
                        }
                    }
                }
            }
        }
        return target
    }

    private fun fail(reason: String): File? {
        _downloadStatus.value = DownloadStatus.Failed(reason)
        return null
    }

    override fun clearDownloadStatus() {
        _downloadStatus.value = DownloadStatus.Idle
    }

    private companion object {
        const val SERVICE_UNAVAILABLE = 503
        const val DOWNLOAD_DIRECTORY = "updates"
        const val DOWNLOAD_FILE_NAME = "myfitnesslog-update.apk"
    }
}

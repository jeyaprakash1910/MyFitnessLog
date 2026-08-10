package com.myfitnesslog.feature.update.data

import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.feature.update.data.remote.AppUpdateApi
import com.myfitnesslog.feature.update.domain.DownloadStatus
import com.myfitnesslog.feature.update.domain.UpdateStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.SocketPolicy
import java.time.Duration
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Transport-level tests for the update check and download.
 *
 * Runs under Robolectric because the download writes into the application's cache
 * directory, and against MockWebServer so the HTTP contract (which path, which
 * status codes, what a truncated body does) is exercised rather than mocked away.
 *
 * The behaviour these pin down is mostly about *not* failing loudly: an update
 * check is a background courtesy and every way it can go wrong has to end as a
 * quiet [UpdateStatus.Unavailable].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AppUpdateRepositoryImplTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun repository(
        installedVersion: String = "1.1.0",
        readTimeout: Duration = Duration.ofSeconds(10),
    ): AppUpdateRepositoryImpl {
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .client(OkHttpClient.Builder().readTimeout(readTimeout).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(AppUpdateApi::class.java)

        return AppUpdateRepositoryImpl(
            api = api,
            context = ApplicationProvider.getApplicationContext(),
            // Unconfined: these tests only need the suspend calls to have finished
            // when they return, and an unconfined dispatcher runs the withContext
            // body eagerly on the calling thread rather than needing the repository's
            // dispatcher to share runTest's scheduler.
            ioDispatcher = UnconfinedTestDispatcher(),
            installedVersion = installedVersion,
        )
    }

    private fun jsonResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun `a newer backend version is reported as available with its notes and size`() = runTest {
        server.enqueue(
            jsonResponse(
                """{"versionName":"1.2.0","releaseNotes":"Editable notes.","sizeBytes":2048}""",
            ),
        )

        val repository = repository(installedVersion = "1.1.0")
        repository.check()

        val status = repository.status.value
        assertTrue("expected Available but was $status", status is UpdateStatus.Available)
        status as UpdateStatus.Available
        assertEquals("1.2.0", status.latestVersion)
        assertEquals("1.1.0", status.installedVersion)
        assertEquals("Editable notes.", status.releaseNotes)
        assertEquals(2048L, status.sizeBytes)

        assertEquals("/api/v1/app/latest-version", server.takeRequest().path)
    }

    @Test
    fun `the same version is up to date, not available`() = runTest {
        server.enqueue(jsonResponse("""{"versionName":"1.1.0"}"""))

        val repository = repository(installedVersion = "1.1.0")
        repository.check()

        assertEquals(UpdateStatus.UpToDate("1.1.0"), repository.status.value)
    }

    @Test
    fun `an older backend version never offers a downgrade`() = runTest {
        // Reachable if a release is deleted or a tag is rolled back. Offering the
        // older build would be worse than offering nothing: Android refuses an
        // install whose versionCode decreased, so it could only ever fail.
        server.enqueue(jsonResponse("""{"versionName":"1.0.0"}"""))

        val repository = repository(installedVersion = "1.1.0")
        repository.check()

        assertEquals(UpdateStatus.UpToDate("1.1.0"), repository.status.value)
    }

    @Test
    fun `a backend with updates unconfigured reports unavailable, not an error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val repository = repository()
        repository.check()

        assertTrue(repository.status.value is UpdateStatus.Unavailable)
    }

    @Test
    fun `an unreachable backend reports unavailable rather than throwing`() = runTest {
        // The everyday case: the free instance is asleep or the phone is off-network.
        server.shutdown()

        val repository = repository()
        repository.check()

        assertTrue(repository.status.value is UpdateStatus.Unavailable)
    }

    /**
     * The 2026-08-10 failure: a phone on 1.5.0 reported "could not reach the
     * server" and could not see the 1.6.0 release.
     *
     * The backend was not unreachable. It was asleep. The free Render instance
     * spins down after inactivity and the first request waits for a container to
     * boot, 100.8s in the deploy log of 2026-08-05, while OkHttp defaults every
     * timeout to 10 seconds. Simulated here with a server that accepts the
     * connection and never answers, which is exactly what a sleeping instance
     * does.
     *
     * The wording matters as much as the status. "Could not reach the server"
     * reads as something broken and invites no second attempt; the whole remedy
     * here is to try again once the container is up.
     */
    @Test
    fun `a sleeping backend says it is waking up, not that it is unreachable`() = runTest {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        val repository = repository(readTimeout = Duration.ofMillis(200))
        repository.check()

        val status = repository.status.value
        assertTrue(status is UpdateStatus.Unavailable)
        assertTrue(
            "expected a wake-up hint, was: ${(status as UpdateStatus.Unavailable).reason}",
            status.reason.contains("waking up", ignoreCase = true),
        )
    }

    @Test
    fun `a malformed version string reports unavailable`() = runTest {
        server.enqueue(jsonResponse("""{"versionName":"nightly-build"}"""))

        val repository = repository()
        repository.check()

        assertTrue(repository.status.value is UpdateStatus.Unavailable)
    }

    @Test
    fun `a second check reuses the settled answer unless forced`() = runTest {
        server.enqueue(jsonResponse("""{"versionName":"1.1.0"}"""))
        server.enqueue(jsonResponse("""{"versionName":"1.3.0"}"""))

        val repository = repository(installedVersion = "1.1.0")
        repository.check()
        repository.check()

        // Still the first answer: two surfaces both calling check() on launch must
        // not produce two requests.
        assertEquals(UpdateStatus.UpToDate("1.1.0"), repository.status.value)
        assertEquals(1, server.requestCount)

        repository.check(force = true)
        assertTrue(repository.status.value is UpdateStatus.Available)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `an unavailable result is retried on the next check without forcing`() = runTest {
        // Unlike a settled answer, "couldn't tell" must not be cached: the backend
        // waking up is exactly the case the next check needs to catch.
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(jsonResponse("""{"versionName":"1.2.0"}"""))

        val repository = repository(installedVersion = "1.1.0")
        repository.check()
        assertTrue(repository.status.value is UpdateStatus.Unavailable)

        repository.check()
        assertTrue(repository.status.value is UpdateStatus.Available)
    }

    @Test
    fun `the apk is written to cache and progress ends at complete`() = runTest {
        val apkBytes = ByteArray(4096) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/vnd.android.package-archive")
                .setBody(Buffer().write(apkBytes)),
        )

        val repository = repository()
        val file = repository.downloadApk()

        requireNotNull(file) { "download returned null" }
        assertArrayEqualsMessage(apkBytes, file.readBytes())
        assertEquals(DownloadStatus.ReadyToInstall, repository.downloadStatus.value)
        assertEquals("/api/v1/app/apk", server.takeRequest().path)
    }

    @Test
    fun `a failed download reports failure and returns null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))

        val repository = repository()

        assertEquals(null, repository.downloadApk())
        assertTrue(repository.downloadStatus.value is DownloadStatus.Failed)
    }

    @Test
    fun `only one downloaded apk is ever kept on disk`() = runTest {
        val first = ByteArray(1024) { 1 }
        val second = ByteArray(2048) { 2 }
        server.enqueue(MockResponse().setBody(Buffer().write(first)))
        server.enqueue(MockResponse().setBody(Buffer().write(second)))

        val repository = repository()
        val firstFile = requireNotNull(repository.downloadApk())
        val secondFile = requireNotNull(repository.downloadApk())

        // The APK is tens of megabytes in reality, so a stale copy per check would
        // quietly consume real storage.
        assertEquals(1, requireNotNull(secondFile.parentFile).listFiles()?.size)
        assertEquals(firstFile.absolutePath, secondFile.absolutePath)
        assertEquals(second.size, secondFile.readBytes().size)
    }

    @Test
    fun `clearing the download status leaves the file in place for the installer`() = runTest {
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(512))))

        val repository = repository()
        val file = requireNotNull(repository.downloadApk())

        repository.clearDownloadStatus()

        assertEquals(DownloadStatus.Idle, repository.downloadStatus.value)
        // Deleting here would break the very install the file was fetched for: the
        // package installer reads it after control has returned to the app.
        assertTrue("the APK must survive a status reset", file.exists())
    }

    private fun assertArrayEqualsMessage(expected: ByteArray, actual: ByteArray) {
        assertEquals("downloaded byte count", expected.size, actual.size)
        assertTrue("downloaded bytes differ", expected.contentEquals(actual))
    }
}

package com.myfitnesslog.core.network

import com.myfitnesslog.core.di.NetworkModule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The HTTP client's timeouts, pinned.
 *
 * These are not arbitrary numbers and OkHttp's defaults are wrong for this
 * deployment. The backend runs on a free Render instance that spins down after
 * inactivity, so the first request after a quiet period waits for a container to
 * boot: **22 seconds** measured on 2026-08-10, and **100.8 seconds** in the
 * deploy log of 2026-08-05. Every OkHttp timeout defaults to 10 seconds, so that
 * first request could not succeed.
 *
 * It surfaced as an update check reporting "could not reach the server" on a
 * phone running 1.5.0, which meant an installed build could not see the 1.6.0
 * release at all. Background sync hid the same fault, because WorkManager retried
 * and the second attempt found a warm server.
 *
 * A test rather than a comment because the failure is invisible in development:
 * a local backend answers instantly, so nothing here would ever notice the
 * timeouts drifting back to the defaults.
 */
class OkHttpTimeoutTest {

    private val client = NetworkModule.provideOkHttpClient()

    @Test
    fun readTimeoutCoversAColdStart() {
        // The worst cold start observed is 100.8s. Anything at or below that would
        // reproduce the bug this test exists for.
        assertTrue(
            "read timeout must cover a ~100s cold start, was ${client.readTimeoutMillis}ms",
            client.readTimeoutMillis >= TimeUnit.SECONDS.toMillis(110).toInt(),
        )
    }

    /**
     * Connect stays short on purpose. A genuinely unreachable network should fail
     * quickly rather than making the user wait out the cold-start budget; it is
     * the *read* that has to be patient, because a sleeping Render instance
     * accepts the connection immediately and only then starts booting.
     */
    @Test
    fun connectTimeoutStaysShortSoADeadNetworkFailsFast() {
        assertEquals(TimeUnit.SECONDS.toMillis(15).toInt(), client.connectTimeoutMillis)
    }

    /**
     * No overall call timeout. It would cap the response body too, and the 8.7 MB
     * APK for an in-app update streams through this same client (ADR-0016). Zero
     * is OkHttp's "no timeout".
     */
    @Test
    fun thereIsNoCallTimeoutBecauseTheApkDownloadStreamsThroughThisClient() {
        assertEquals(0, client.callTimeoutMillis)
    }

    @Test
    fun writeTimeoutIsSet() {
        assertEquals(TimeUnit.SECONDS.toMillis(30).toInt(), client.writeTimeoutMillis)
    }
}

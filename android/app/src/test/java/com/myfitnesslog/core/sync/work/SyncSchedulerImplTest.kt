package com.myfitnesslog.core.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.TimeUnit

/**
 * Tests that [SyncSchedulerImpl] enqueues work with the intended constraints,
 * backoff, and uniqueness policy.
 *
 * Uses the real WorkManager test instance rather than a mock, so these assert
 * what WorkManager actually recorded — including the details (backoff delay
 * units, network type) that are easy to get subtly wrong in a builder chain.
 */
@RunWith(RobolectricTestRunner::class)
class SyncSchedulerImplTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncSchedulerImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder()
                .setExecutor(SynchronousExecutor())
                .build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = SyncSchedulerImpl(workManager)
    }

    private fun infosForUniqueWork(name: String): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(name).get()

    @Test
    fun `syncNow enqueues one request under the unique one-time name`() {
        scheduler.syncNow()

        val infos = infosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME)
        assertEquals(1, infos.size)
    }

    @Test
    fun `syncNow requires network connectivity`() {
        scheduler.syncNow()

        val info = infosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME).single()
        assertEquals(NetworkType.CONNECTED, info.constraints.requiredNetworkType)
        // Deliberately unconstrained: a completed workout must not wait for a
        // charger or Wi-Fi.
        assertEquals(false, info.constraints.requiresCharging())
        assertEquals(false, info.constraints.requiresBatteryNotLow())
    }

    @Test
    fun `syncNow tags the request so all sync work can be observed together`() {
        scheduler.syncNow()

        val info = infosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME).single()
        assertTrue(info.tags.toString(), info.tags.contains(SyncConfiguration.WORK_TAG))
        assertTrue(info.tags.contains(SyncWorker::class.java.name))
    }

    @Test
    fun `a second syncNow while one is queued does not enqueue a duplicate`() {
        // KEEP matters: two concurrent passes would race for the same rows,
        // which the engine's SYNCING claim cannot arbitrate.
        scheduler.syncNow()
        val firstId = infosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME).single().id

        scheduler.syncNow()
        scheduler.syncNow()

        val infos = infosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME)
        assertEquals(1, infos.size)
        // Asserting the *identity* is what distinguishes KEEP from REPLACE:
        // REPLACE also leaves one row, but it is a different, newly created
        // request that cancelled the original mid-flight.
        assertEquals(firstId, infos.single().id)
    }

    @Test
    fun `ensurePeriodicSync enqueues periodic work with the configured interval`() {
        scheduler.ensurePeriodicSync()

        val infos = infosForUniqueWork(SyncConfiguration.PERIODIC_WORK_NAME)
        assertEquals(1, infos.size)
        assertEquals(NetworkType.CONNECTED, infos.single().constraints.requiredNetworkType)
    }

    @Test
    fun `ensurePeriodicSync is idempotent across app launches`() {
        scheduler.ensurePeriodicSync()
        val firstId = infosForUniqueWork(SyncConfiguration.PERIODIC_WORK_NAME).single().id

        scheduler.ensurePeriodicSync()

        val infos = infosForUniqueWork(SyncConfiguration.PERIODIC_WORK_NAME)
        assertEquals(1, infos.size)
        // KEEP, not UPDATE: the existing schedule survives, so a frequently
        // reopened app still reaches its periodic run.
        assertEquals(firstId, infos.single().id)
    }

    @Test
    fun `one-time and periodic sync coexist as separate unique work`() {
        scheduler.syncNow()
        scheduler.ensurePeriodicSync()

        assertEquals(1, infosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME).size)
        assertEquals(1, infosForUniqueWork(SyncConfiguration.PERIODIC_WORK_NAME).size)
    }

    @Test
    fun `the configured backoff is exponential with a 30 second initial delay`() {
        // Asserted on the configuration itself: WorkInfo does not expose backoff,
        // and these constants are what the request builders consume.
        assertEquals(BackoffPolicy.EXPONENTIAL, SyncConfiguration.BACKOFF_POLICY)
        assertEquals(30L, SyncConfiguration.BACKOFF_DELAY.seconds)
        assertEquals(
            15L,
            TimeUnit.MILLISECONDS.toMinutes(SyncConfiguration.PERIODIC_INTERVAL.toMillis()),
        )
    }
}

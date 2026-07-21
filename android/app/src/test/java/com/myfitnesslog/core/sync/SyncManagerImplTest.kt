package com.myfitnesslog.core.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.myfitnesslog.core.sync.work.SyncConfiguration
import com.myfitnesslog.core.sync.work.SyncScheduler
import com.myfitnesslog.core.sync.work.SyncSchedulerImpl
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the application-facing synchronization entry point, including the
 * app-startup behaviour that nothing else exercises end to end.
 */
@RunWith(RobolectricTestRunner::class)
class SyncManagerImplTest {

    private class RecordingScheduler : SyncScheduler {
        val calls = mutableListOf<String>()
        override fun syncNow() { calls += "syncNow" }
        override fun ensurePeriodicSync() { calls += "ensurePeriodicSync" }
    }

    private val scheduler = RecordingScheduler()
    private val manager = SyncManagerImpl(scheduler)

    @Test
    fun `app start registers the periodic schedule and requests an immediate pass`() {
        manager.onAppStart()

        // The immediate pass matters: without it, anything left pending by the
        // previous run could wait up to a full periodic interval.
        assertEquals(listOf("ensurePeriodicSync", "syncNow"), scheduler.calls)
    }

    @Test
    fun `syncNow requests a single pass and nothing else`() {
        manager.syncNow()

        assertEquals(listOf("syncNow"), scheduler.calls)
    }

    @Test
    fun `the trigger seam requests the same pass as the manual entry point`() {
        val trigger: SyncTrigger = manager

        trigger.requestSync()

        assertEquals(listOf("syncNow"), scheduler.calls)
    }

    /**
     * The startup path against a real WorkManager, proving the two enqueues
     * coexist rather than colliding on a shared unique name.
     */
    @RunWith(RobolectricTestRunner::class)
    class AgainstRealWorkManager {

        private lateinit var workManager: WorkManager
        private lateinit var manager: SyncManagerImpl

        @Before
        fun setUp() {
            val context = ApplicationProvider.getApplicationContext<Context>()
            WorkManagerTestInitHelper.initializeTestWorkManager(
                context,
                Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
            )
            workManager = WorkManager.getInstance(context)
            manager = SyncManagerImpl(SyncSchedulerImpl(workManager))
        }

        @Test
        fun `app start enqueues both the periodic and the immediate work`() {
            manager.onAppStart()

            assertEquals(
                1,
                workManager.getWorkInfosForUniqueWork(SyncConfiguration.PERIODIC_WORK_NAME)
                    .get().size,
            )
            assertEquals(
                1,
                workManager.getWorkInfosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME)
                    .get().size,
            )
        }

        @Test
        fun `repeated app starts do not accumulate work`() {
            // A process restart must not queue a second periodic schedule.
            manager.onAppStart()
            val periodicId = workManager
                .getWorkInfosForUniqueWork(SyncConfiguration.PERIODIC_WORK_NAME).get().single().id

            manager.onAppStart()
            manager.onAppStart()

            val periodic =
                workManager.getWorkInfosForUniqueWork(SyncConfiguration.PERIODIC_WORK_NAME).get()
            assertEquals(1, periodic.size)
            assertEquals(periodicId, periodic.single().id)
            assertEquals(
                1,
                workManager.getWorkInfosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME)
                    .get().size,
            )
        }

        @Test
        fun `many rapid sync requests collapse into one unique work item`() {
            // Repositories call this after every write; it must stay cheap.
            repeat(25) { manager.requestSync() }

            assertEquals(
                1,
                workManager.getWorkInfosForUniqueWork(SyncConfiguration.ONE_TIME_WORK_NAME)
                    .get().size,
            )
        }
    }
}

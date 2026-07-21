package com.myfitnesslog.core.sync.work

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.myfitnesslog.core.sync.engine.SyncEngine
import com.myfitnesslog.core.sync.engine.SyncRecovery
import com.myfitnesslog.core.sync.model.SyncEntityType
import com.myfitnesslog.core.sync.model.SyncFailure
import com.myfitnesslog.core.sync.model.SyncFailureReason
import com.myfitnesslog.core.sync.model.SyncResult
import com.myfitnesslog.core.sync.model.SyncSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * Tests [SyncWorker]'s two responsibilities: running recovery before the pass,
 * and translating a [SyncResult] into a WorkManager verdict.
 *
 * The engine and recovery are fakes — the worker is supposed to contain no
 * synchronization logic, and these tests are what hold it to that.
 */
@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {

    private class FakeSyncEngine(var result: SyncResult = SyncResult.NothingToSync) : SyncEngine {
        var callCount = 0
        override suspend fun sync(): SyncResult {
            callCount++
            return result
        }
    }

    private class FakeSyncRecovery(var recovered: Int = 0) : SyncRecovery {
        var callCount = 0
        override suspend fun recoverStaleSyncing(): Int {
            callCount++
            return recovered
        }
    }

    /** Records the order of calls, to prove recovery precedes the pass. */
    private val callOrder = mutableListOf<String>()

    private val engine = FakeSyncEngine()
    private val recovery = FakeSyncRecovery()

    private fun buildWorker(): SyncWorker {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(
                object : androidx.work.WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = SyncWorker(
                        appContext = appContext,
                        params = workerParameters,
                        syncEngine = object : SyncEngine {
                            override suspend fun sync(): SyncResult {
                                callOrder += "sync"
                                return engine.sync()
                            }
                        },
                        syncRecovery = object : SyncRecovery {
                            override suspend fun recoverStaleSyncing(): Int {
                                callOrder += "recover"
                                return recovery.recoverStaleSyncing()
                            }
                        },
                    )
                },
            )
            .build()
    }

    private fun failure(reason: SyncFailureReason) = SyncFailure(
        entityType = SyncEntityType.ROUTINE,
        id = UUID.randomUUID(),
        reason = reason,
        message = "test",
    )

    // ---- Result mapping ---------------------------------------------------

    @Test
    fun `nothing to sync succeeds`() = runTest {
        engine.result = SyncResult.NothingToSync

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
    }

    @Test
    fun `a fully successful pass succeeds`() = runTest {
        engine.result = SyncResult.Success(SyncSummary(uploaded = 3))

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
    }

    @Test
    fun `a retryable network failure asks WorkManager to retry`() = runTest {
        engine.result = SyncResult.Failure(
            SyncSummary(failures = listOf(failure(SyncFailureReason.NETWORK))),
        )

        assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
    }

    @Test
    fun `a retryable server failure in a partial pass asks for retry`() = runTest {
        engine.result = SyncResult.Partial(
            SyncSummary(uploaded = 2, failures = listOf(failure(SyncFailureReason.SERVER_ERROR))),
        )

        assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
    }

    @Test
    fun `a permanent rejection succeeds instead of burning retries`() = runTest {
        // The rows are already marked FAILED and remain on disk. Retrying an
        // unchanged rejected payload would fail identically forever and delay
        // the work that can actually succeed.
        engine.result = SyncResult.Partial(
            SyncSummary(uploaded = 1, failures = listOf(failure(SyncFailureReason.REJECTED))),
        )

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
    }

    @Test
    fun `a mix of permanent and retryable failures still retries`() = runTest {
        engine.result = SyncResult.Partial(
            SyncSummary(
                uploaded = 1,
                failures = listOf(
                    failure(SyncFailureReason.REJECTED),
                    failure(SyncFailureReason.NETWORK),
                ),
            ),
        )

        assertEquals(ListenableWorker.Result.retry(), buildWorker().doWork())
    }

    @Test
    fun `a pass that only skipped work succeeds without retrying`() = runTest {
        // Skips with no failures cannot happen in practice, but the mapping
        // should not retry on the absence of a retryable failure.
        engine.result = SyncResult.Partial(SyncSummary(uploaded = 1))

        assertEquals(ListenableWorker.Result.success(), buildWorker().doWork())
    }

    // ---- Recovery ordering ------------------------------------------------

    @Test
    fun `recovery runs before the sync pass, every time`() = runTest {
        engine.result = SyncResult.NothingToSync

        buildWorker().doWork()

        assertEquals(listOf("recover", "sync"), callOrder)
        assertEquals(1, recovery.callCount)
        assertEquals(1, engine.callCount)
    }

    @Test
    fun `recovery runs even when there is nothing stranded`() = runTest {
        recovery.recovered = 0

        buildWorker().doWork()

        assertEquals(1, recovery.callCount)
    }

    @Test
    fun `a pass after process death recovers stranded rows and still syncs`() = runTest {
        recovery.recovered = 4
        engine.result = SyncResult.Success(SyncSummary(uploaded = 4))

        val result = buildWorker().doWork()

        assertEquals(listOf("recover", "sync"), callOrder)
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `recovery still runs when the pass will fail`() = runTest {
        engine.result = SyncResult.Failure(
            SyncSummary(failures = listOf(failure(SyncFailureReason.NETWORK))),
        )

        val result = buildWorker().doWork()

        assertTrue(callOrder.first() == "recover")
        assertEquals(ListenableWorker.Result.retry(), result)
    }
}

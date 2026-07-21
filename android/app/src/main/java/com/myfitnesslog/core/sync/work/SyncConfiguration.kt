package com.myfitnesslog.core.sync.work

import androidx.work.BackoffPolicy
import java.time.Duration

/**
 * Scheduling constants for background synchronization.
 *
 * Gathered in one object so the policy is stated once and asserted directly by
 * the scheduler tests, rather than being spread across builder calls.
 */
object SyncConfiguration {

    /**
     * Unique name for the one-shot sync request.
     *
     * Enqueuing under a unique name is what prevents two passes from running at
     * once: the engine claims rows with SYNCING, and two concurrent passes would
     * race for the same rows. `ExistingWorkPolicy.KEEP` means a request arriving
     * while sync is already queued is dropped rather than duplicating it.
     */
    const val ONE_TIME_WORK_NAME: String = "sync-now"

    /** Unique name for the recurring background sync. */
    const val PERIODIC_WORK_NAME: String = "sync-periodic"

    /** Tag applied to every sync request, so all of them can be observed together. */
    const val WORK_TAG: String = "sync"

    /**
     * Exponential backoff, matching SYNC.md §16.
     *
     * The engine performs no retry logic of its own — it does one pass and
     * reports. Rescheduling is entirely WorkManager's job, which is why the
     * Worker only has to choose between `retry()` and `success()`.
     */
    val BACKOFF_POLICY: BackoffPolicy = BackoffPolicy.EXPONENTIAL

    /** First retry delay; WorkManager doubles it on each subsequent attempt. */
    val BACKOFF_DELAY: Duration = Duration.ofSeconds(30)

    /**
     * Periodic sync interval.
     *
     * Fifteen minutes is WorkManager's minimum for periodic work; anything
     * smaller is silently raised to it. Offline-first means nothing the user does
     * waits on this, so a longer interval costs nothing but battery savings.
     */
    val PERIODIC_INTERVAL: Duration = Duration.ofMinutes(15)
}

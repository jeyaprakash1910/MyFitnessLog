package com.myfitnesslog.core.sync.work

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import javax.inject.Inject

/**
 * Enqueues background synchronization.
 *
 * The scheduling policy lives here rather than at the call sites, so every
 * trigger added in a later phase gets the same constraints, backoff, and
 * uniqueness guarantees without having to remember them.
 */
interface SyncScheduler {

    /** Requests one sync as soon as the network allows. */
    fun syncNow()

    /** Ensures the recurring background sync is registered. */
    fun ensurePeriodicSync()
}

class SyncSchedulerImpl @Inject constructor(
    private val workManager: WorkManager,
) : SyncScheduler {

    override fun syncNow() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(SyncConstraints.NETWORK_CONNECTED)
            .setBackoffCriteria(
                SyncConfiguration.BACKOFF_POLICY,
                SyncConfiguration.BACKOFF_DELAY,
            )
            .addTag(SyncConfiguration.WORK_TAG)
            .build()

        workManager.enqueueUniqueWork(
            SyncConfiguration.ONE_TIME_WORK_NAME,
            // KEEP, not REPLACE: a sync already queued or running will pick up
            // whatever is pending anyway, and replacing a *running* pass would
            // cancel it mid-upload, stranding rows in SYNCING.
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    override fun ensurePeriodicSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(SyncConfiguration.PERIODIC_INTERVAL)
            .setConstraints(SyncConstraints.NETWORK_CONNECTED)
            .setBackoffCriteria(
                SyncConfiguration.BACKOFF_POLICY,
                SyncConfiguration.BACKOFF_DELAY,
            )
            .addTag(SyncConfiguration.WORK_TAG)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncConfiguration.PERIODIC_WORK_NAME,
            // KEEP preserves the existing schedule across app launches; UPDATE
            // would reset the interval every time the app started, so a
            // frequently-opened app would never reach a periodic run.
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}

package com.myfitnesslog.core.sync.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.myfitnesslog.core.sync.engine.SyncEngine
import com.myfitnesslog.core.sync.engine.SyncRecovery
import com.myfitnesslog.core.sync.restore.RefreshManager
import com.myfitnesslog.core.sync.restore.RefreshOutcome
import com.myfitnesslog.core.sync.model.SyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs one synchronization pass in the background.
 *
 * Intentionally as thin as it can be: recover stranded claims, run the engine,
 * translate the result into a WorkManager verdict. It contains no
 * synchronization logic of its own, so all of that stays unit-testable without
 * WorkManager.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngine,
    private val syncRecovery: SyncRecovery,
    private val refreshManager: RefreshManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // Must precede the pass: rows left SYNCING by a killed process are
        // invisible to the pending queries and would otherwise never upload.
        val recovered = syncRecovery.recoverStaleSyncing()
        if (recovered > 0) {
            Log.i(TAG, "Recovered $recovered row(s) stranded in SYNCING.")
        }

        val result = syncEngine.sync()
        Log.i(TAG, "Sync finished: ${result::class.simpleName} ${result.summary}")

        // Pull after pushing, never before (ADR-0017 Stage 2). Refresh defers
        // while anything is still queued, so running it here, once the pass has
        // drained what it can, is the only point where the backend's answer is
        // worth having. Its result deliberately does not affect the Worker's:
        // a failed refresh means the local copy is briefly stale, which is the
        // app's ordinary offline state and not a reason to burn a retry.
        when (val refresh = refreshManager.refresh()) {
            is RefreshOutcome.Applied ->
                if (refresh.changedAnything) Log.i(TAG, "Refresh applied: $refresh")
            is RefreshOutcome.Failed -> Log.i(TAG, "Refresh skipped: ${refresh.reason}")
            RefreshOutcome.Deferred -> Log.i(TAG, "Refresh deferred: uploads still pending.")
        }

        return when (result) {
            is SyncResult.NothingToSync, is SyncResult.Success -> Result.success()

            is SyncResult.Partial, is SyncResult.Failure ->
                if (result.summary.hasRetryableFailure) {
                    // Network or server trouble — WorkManager reschedules with
                    // the configured exponential backoff.
                    Result.retry()
                } else {
                    // Every failure was a rejected payload, which would fail
                    // identically forever. The rows are already marked FAILED and
                    // stay on disk, so nothing is lost; burning retries on them
                    // would only delay the work that can succeed.
                    Result.success()
                }
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}

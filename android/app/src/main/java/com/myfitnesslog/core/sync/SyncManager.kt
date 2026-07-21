package com.myfitnesslog.core.sync

import com.myfitnesslog.core.sync.work.SyncScheduler
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The application-facing entry point to synchronization.
 *
 * Everything above the data layer — the Application, and any future settings
 * screen or pull-to-refresh gesture — goes through this rather than touching
 * `SyncScheduler` or `SyncEngine` directly. It is the one place that knows both
 * "start the recurring schedule" and "sync now" are things an app can ask for.
 *
 * No UI is wired to [syncNow] yet; it exists so a manual trigger can be added
 * later without reopening the synchronization layer, and so integration tests
 * have a realistic entry point.
 */
interface SyncManager : SyncTrigger {

    /**
     * Called once when the app starts: registers the recurring background sync
     * and requests an immediate pass for anything left pending from last run.
     */
    fun onAppStart()

    /**
     * Requests a synchronization pass now (network permitting).
     *
     * Fire-and-forget, per [SyncTrigger.requestSync] — this is the same request,
     * named for human callers.
     */
    fun syncNow()
}

@Singleton
class SyncManagerImpl @Inject constructor(
    private val scheduler: SyncScheduler,
) : SyncManager {

    override fun onAppStart() {
        scheduler.ensurePeriodicSync()
        // Covers everything the previous run could not finish: rows left PENDING
        // by a crash, an offline session, or a workout completed just before the
        // app was killed. The periodic schedule alone could leave that waiting up
        // to fifteen minutes.
        scheduler.syncNow()
    }

    override fun syncNow() = scheduler.syncNow()

    override fun requestSync() = scheduler.syncNow()
}

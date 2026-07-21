package com.myfitnesslog.core.sync

/**
 * Requests that pending local changes be synchronized soon.
 *
 * This is the seam between the data layer and the scheduling layer. Repositories
 * depend on this rather than on `SyncScheduler` for two reasons:
 *
 *  - **Narrowness.** A repository has no business registering *periodic* work.
 *    Handing it the full scheduler would make that possible by accident.
 *  - **Direction.** `core.sync.work` knows about WorkManager; the feature data
 *    layer must not. Depending on this one-method interface keeps repositories
 *    ignorant of how — or even whether — scheduling happens.
 *
 * ## Contract
 *
 * [requestSync] is **fire-and-forget**: it returns immediately, never blocks,
 * never throws, and never reports whether synchronization succeeded. A caller
 * that waited on it would be reintroducing exactly the network dependency the
 * offline-first architecture exists to remove (ADR-0002).
 *
 * It is also safe to call redundantly. Requests collapse into a single unique
 * work item, so callers should err towards calling it after every local write
 * rather than trying to be clever about when a sync is "worth it".
 */
interface SyncTrigger {

    /** Asks for a synchronization pass as soon as the network allows. */
    fun requestSync()
}

package com.myfitnesslog.core.sync.engine

import com.myfitnesslog.core.sync.model.SyncResult

/**
 * Uploads all locally pending user data to the backend, once.
 *
 * The engine is deliberately platform-independent: it knows nothing about
 * WorkManager, connectivity callbacks, or the Android lifecycle. It is a
 * suspending function that does one pass and reports what happened, which makes
 * it testable with fake sources and MockWebServer alone. Deciding *when* to call
 * it — and whether a failed pass deserves a retry — belongs to the scheduling
 * layer in a later phase.
 */
interface SyncEngine {

    /**
     * Performs one synchronization pass.
     *
     * Never throws for an upload failure: a partial result is a normal outcome
     * and is reported through [SyncResult]. Progress already made is always kept.
     */
    suspend fun sync(): SyncResult
}

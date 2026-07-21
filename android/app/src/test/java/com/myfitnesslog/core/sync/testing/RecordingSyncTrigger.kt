package com.myfitnesslog.core.sync.testing

import com.myfitnesslog.core.sync.SyncTrigger

/**
 * A [SyncTrigger] that records requests instead of scheduling anything.
 *
 * Most tests use it simply to satisfy the constructor — a repository test has no
 * interest in scheduling. The trigger tests use [requestCount] to assert that a
 * local write actually asked for a sync, and that a no-op write did not.
 */
class RecordingSyncTrigger : SyncTrigger {

    var requestCount = 0
        private set

    override fun requestSync() {
        requestCount++
    }

    fun reset() {
        requestCount = 0
    }
}

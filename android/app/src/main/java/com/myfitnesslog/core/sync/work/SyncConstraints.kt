package com.myfitnesslog.core.sync.work

import androidx.work.Constraints
import androidx.work.NetworkType

/**
 * The conditions under which background synchronization may run.
 *
 * Only connectivity is required. Deliberately *not* required:
 *
 *  - **charging / idle** — workout data is small and the user may go days
 *    between charges; history should not wait on a power cable.
 *  - **unmetered network** — a completed workout is a few kilobytes, and
 *    refusing to sync it off Wi-Fi would strand data for the whole gym session.
 *  - **battery not low** — losing history is worse than a marginal battery cost.
 *
 * SYNC.md §9 asks only that synchronization work require network connectivity.
 */
object SyncConstraints {

    val NETWORK_CONNECTED: Constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}

package com.myfitnesslog.core.data.local

/**
 * Local synchronization state for a mutable, user-created record (SYNC.md §15).
 *
 * Reference data (exercises/categories) is download-only and does not carry a
 * sync status. User data (routines, workouts) is created offline as [PENDING]
 * and later uploaded by the synchronization layer (Milestone 9). The status is
 * internal and never shown to the user.
 *
 * Added to mutable entities from creation so the sync column exists before sync
 * is built, avoiding a destructive Room migration later.
 */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    FAILED,
}

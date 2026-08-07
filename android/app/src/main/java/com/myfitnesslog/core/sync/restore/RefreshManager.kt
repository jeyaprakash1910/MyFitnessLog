package com.myfitnesslog.core.sync.restore

/**
 * Brings this device's local copy back in line with the backend (ADR-0017,
 * Stage 2).
 *
 * Where [RestoreManager] runs once against an empty database and can therefore
 * ignore conflicts entirely, this runs against a database with real data in it and
 * has to decide what happens when the two copies disagree. One rule settles it:
 *
 * > **The backend wins for any row with no pending local change.**
 *
 * The second half of that sentence is what makes it safe. A row the outbox still
 * owns, anything not `SYNCED`, is left completely alone until it has been
 * uploaded. Without that carve-out a refresh arriving between "user logs a set"
 * and "set reaches the server" would overwrite the set with the server's older
 * view and silently destroy work the user just did. That is the single most
 * dangerous thing a pull can do, and it is the reason refresh runs *after* the
 * upload pass rather than beside it.
 *
 * This is what finally makes ADR-0003 true in practice. Until now the backend was
 * the declared source of truth that no client ever read.
 */
interface RefreshManager {

    /**
     * Pulls the backend's current state and applies it locally.
     *
     * Never throws, for the same reason [RestoreManager.restoreIfEmpty] does not:
     * a failed refresh leaves the user exactly where they already were, which is a
     * working offline app, and must never break a background pass.
     */
    suspend fun refresh(): RefreshOutcome
}

/** What a refresh changed. */
sealed interface RefreshOutcome {

    /**
     * Refresh was skipped because the outbox still had work.
     *
     * Pulling while uploads are outstanding is exactly the case where the backend
     * is known to be behind, so its answer is not worth having yet.
     */
    data object Deferred : RefreshOutcome

    data class Applied(
        val routinesUpdated: Int,
        val routinesRemoved: Int,
        val sessionsUpdated: Int,
        val sessionsRemoved: Int,
        val skippedPendingLocal: Int,
    ) : RefreshOutcome {
        val changedAnything: Boolean
            get() = routinesUpdated + routinesRemoved + sessionsUpdated + sessionsRemoved > 0
    }

    data class Failed(val reason: String) : RefreshOutcome
}

package com.myfitnesslog.core.sync.restore

/**
 * Rebuilds this device's local database from the backend when it has nothing of
 * its own (ADR-0017, Stage 1).
 *
 * Until this existed, synchronisation ran in one direction only: the phone could
 * upload everything it knew and never ask for any of it back. A lost, reset or
 * reinstalled device therefore lost its history permanently even though the
 * backend still held a complete copy, which is exactly what happened on
 * 2026-07-22 (CODING_STANDARDS §20c).
 */
interface RestoreManager {

    /**
     * Restores routines and completed workout history, but **only if the local
     * database is empty**.
     *
     * The emptiness precondition is the design, not a limitation to be removed
     * later. With nothing local to disagree with, there is no merge, no
     * last-write-wins rule and no chance of overwriting unsynced work. That is
     * what makes this safe to run automatically at launch, and it is why Stage 2
     * (periodic refresh) is a separate decision with its own conflict rules
     * rather than an extension of this one.
     *
     * Never throws. An unreachable backend is the ordinary case for this app: the
     * instance sleeps, the phone is off-network, and a fresh install may well be
     * happening in a gym. Failing to restore leaves the user exactly where they
     * already were, so it must not be able to break a launch.
     *
     * @return what happened, for logging and tests
     */
    suspend fun restoreIfEmpty(): RestoreOutcome
}

/** The result of a restore attempt. */
sealed interface RestoreOutcome {

    /** The database already had data, so nothing was touched. */
    data object NotNeeded : RestoreOutcome

    /** Nothing to restore: the backend has no routines and no history either. */
    data object NothingToRestore : RestoreOutcome

    data class Restored(
        val routines: Int,
        val sessions: Int,
    ) : RestoreOutcome

    /**
     * The attempt failed and the database was left as it was found.
     *
     * [reason] is for logs, not for the user. There is nothing useful to tell
     * someone whose restore did not run: the next launch simply tries again.
     */
    data class Failed(val reason: String) : RestoreOutcome
}

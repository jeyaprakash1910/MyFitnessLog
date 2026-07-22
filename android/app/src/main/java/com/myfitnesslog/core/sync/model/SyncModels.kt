package com.myfitnesslog.core.sync.model

import java.util.UUID

/**
 * The entity kinds the synchronization engine uploads, in dependency order.
 *
 * The declaration order is the canonical upload sequence documented in SYNC.md
 * §8, and is asserted by the engine's ordering tests.
 */
enum class SyncEntityType {
    ROUTINE,
    ROUTINE_EXERCISE,
    WORKOUT_SESSION_START,
    WORKOUT_EXERCISE,
    WORKOUT_SET,
    WORKOUT_SET_DELETION,
    WORKOUT_SESSION_TRANSITION,
}

/**
 * Why a single upload did not succeed.
 *
 * The distinction that matters is [isRetryable]: a transient failure will very
 * likely succeed on a later attempt, whereas a rejected payload will fail
 * identically forever. Both leave the row FAILED so no data is lost, but only
 * the retryable kind justifies asking the scheduler to run again — that decision
 * belongs to the caller (the Worker, in a later phase), not to the engine.
 */
enum class SyncFailureReason {
    /** No connectivity, timeout, or the request never completed. */
    NETWORK,

    /** 5xx, 408, or 429 — the backend is unwell or throttling, not the payload. */
    SERVER_ERROR,

    /** 4xx other than 408/429 — the request itself was rejected. */
    REJECTED,

    ;

    val isRetryable: Boolean
        get() = this != REJECTED
}

/** One failed upload, identified precisely enough to debug it from a log line. */
data class SyncFailure(
    val entityType: SyncEntityType,
    val id: UUID,
    val reason: SyncFailureReason,
    val message: String,
)

/**
 * One upload that was never attempted because an ancestor failed.
 *
 * Skipping is not a failure: the row is left PENDING and untouched so the next
 * pass retries it naturally once its parent exists on the backend. Reporting it
 * separately from failures is what makes a partial sync legible.
 */
data class SyncSkip(
    val entityType: SyncEntityType,
    val id: UUID,
    val blockedBy: UUID,
)

/**
 * What one synchronization pass actually did.
 *
 * Counts and details are kept together so a caller can log a one-line summary or
 * drill into individual failures without a second query.
 */
data class SyncSummary(
    val uploaded: Int = 0,
    val failures: List<SyncFailure> = emptyList(),
    val skips: List<SyncSkip> = emptyList(),
) {
    val failed: Int get() = failures.size
    val skipped: Int get() = skips.size

    /** True when at least one failure could plausibly succeed on a retry. */
    val hasRetryableFailure: Boolean get() = failures.any { it.reason.isRetryable }

    val isEmpty: Boolean get() = uploaded == 0 && failed == 0 && skipped == 0

    override fun toString(): String =
        "SyncSummary(uploaded=$uploaded, failed=$failed, skipped=$skipped)"
}

/**
 * The outcome of one synchronization pass.
 *
 * Modelled as a result rather than an exception because a partial sync is a
 * normal, expected outcome — some aggregates succeeding while others fail is the
 * common case on a flaky connection, and it must never discard the progress that
 * did succeed.
 */
sealed interface SyncResult {

    val summary: SyncSummary

    /** Nothing was pending. */
    data object NothingToSync : SyncResult {
        override val summary: SyncSummary = SyncSummary()
    }

    /** Everything pending was uploaded. */
    data class Success(override val summary: SyncSummary) : SyncResult

    /** Some uploads succeeded; others failed or were skipped. */
    data class Partial(override val summary: SyncSummary) : SyncResult

    /** Everything attempted failed. */
    data class Failure(override val summary: SyncSummary) : SyncResult
}

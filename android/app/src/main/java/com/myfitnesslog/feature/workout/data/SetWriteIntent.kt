package com.myfitnesslog.feature.workout.data

/**
 * Why a set is being written, which decides whether a COMPLETED session accepts it.
 *
 * ADR-0004 made completed workouts immutable and the repository enforced that for
 * every caller. ADR-0018 then permitted three corrections to a completed workout,
 * and the enforcement was widened to allow `COMPLETED` on set writes generally.
 * That was broader than the decision: it also stopped protecting the *logging*
 * screen, whose writes must never reach a session that has finished.
 *
 * The two callers want different rules, so they say which they are rather than
 * both getting the looser one:
 *
 *  * [LOGGING] - the active workout screen. `IN_PROGRESS` only, as before
 *    ADR-0018. A finished session rejects the write.
 *  * [CORRECTION] - the workout detail screen's correction flow (ADR-0018).
 *    `COMPLETED` too, because correcting what was performed is the entire point.
 *
 * [LOGGING] is the default deliberately. The stricter rule is the safe one to get
 * by accident, and the wider permission is the one worth having to ask for.
 *
 * `DISCARDED` is refused under both. It means the user threw the session away, so
 * there is nothing there to correct.
 */
enum class SetWriteIntent {
    LOGGING,
    CORRECTION,
}

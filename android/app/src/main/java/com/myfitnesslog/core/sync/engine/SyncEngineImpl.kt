package com.myfitnesslog.core.sync.engine

import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.sync.model.SyncEntityType
import com.myfitnesslog.core.sync.model.SyncFailure
import com.myfitnesslog.core.sync.model.SyncFailureReason
import com.myfitnesslog.core.sync.model.SyncResult
import com.myfitnesslog.core.sync.model.SyncSkip
import com.myfitnesslog.core.sync.model.SyncSummary
import com.myfitnesslog.core.sync.source.RoutineExerciseSyncSource
import com.myfitnesslog.core.sync.source.RoutineSyncSource
import com.myfitnesslog.core.sync.source.WorkoutExerciseSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSessionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutExerciseDeletionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSetDeletionSyncSource
import com.myfitnesslog.core.sync.source.WorkoutSetSyncSource
import com.myfitnesslog.core.util.IoDispatcher
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import com.myfitnesslog.feature.routine.data.remote.toAddRequest
import com.myfitnesslog.feature.routine.data.remote.toCreateRequest
import com.myfitnesslog.feature.workout.data.remote.WorkoutLogApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import com.myfitnesslog.feature.workout.data.remote.toAddRequest
import com.myfitnesslog.feature.workout.data.remote.toCompleteRequest
import com.myfitnesslog.feature.workout.data.remote.toDiscardRequest
import com.myfitnesslog.feature.workout.data.remote.toStartRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import retrofit2.Response
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Default [SyncEngine].
 *
 * ## Ordering
 *
 * One pass runs seven phases in exactly the sequence SYNC.md §8 defines:
 *
 * ```
 * Routine → RoutineExercise → WorkoutSession(start)
 *         → WorkoutExercise → WorkoutSet → WorkoutSet(deletions)
 *         → WorkoutSession(complete/discard)
 * ```
 *
 * The order is a correctness requirement, not an optimization: every phase but
 * the first uploads rows whose foreign keys the previous phase created. The
 * session's terminal transition comes last because the backend rejects changes
 * to a session that is no longer IN_PROGRESS — sending it early would lock out
 * the exercises and sets that still need to be written.
 *
 * ## Aggregate isolation
 *
 * A failure stops only its own aggregate. If one routine fails to upload, its
 * exercises are skipped, but every other routine and every workout still syncs.
 * Blocked descendants are left PENDING and untouched so the next pass retries
 * them once the parent exists.
 *
 * ## Idempotency
 *
 * No client-side deduplication is performed. Every create endpoint is idempotent
 * on the client-generated UUID, so replaying an upload whose response was lost is
 * safe and simply returns 200 instead of 201. That is what makes the crude
 * "retry the whole pending set" strategy correct.
 */
class SyncEngineImpl @Inject constructor(
    private val routineSource: RoutineSyncSource,
    private val routineExerciseSource: RoutineExerciseSyncSource,
    private val sessionSource: WorkoutSessionSyncSource,
    private val workoutExerciseSource: WorkoutExerciseSyncSource,
    private val setSource: WorkoutSetSyncSource,
    private val deletionSource: WorkoutSetDeletionSyncSource,
    private val exerciseDeletionSource: WorkoutExerciseDeletionSyncSource,
    private val routineApi: RoutineApi,
    private val sessionApi: WorkoutSessionApi,
    private val logApi: WorkoutLogApi,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : SyncEngine {

    override suspend fun sync(): SyncResult = withContext(ioDispatcher) {
        val pass = SyncPass()

        pass.syncRoutines()
        pass.syncRoutineExercises()
        pass.syncWorkoutSessionStarts()
        pass.syncWorkoutExercises()
        pass.syncWorkoutSets()
        pass.syncWorkoutSetDeletions()
        pass.syncWorkoutExerciseDeletions()
        pass.syncWorkoutSessionTransitions()

        pass.toResult()
    }

    /**
     * Mutable bookkeeping for a single pass.
     *
     * Kept as an inner class so the engine itself holds no state between calls —
     * two concurrent passes would each get their own, and nothing leaks from one
     * sync to the next.
     */
    private inner class SyncPass {

        private var uploaded = 0
        private val failures = mutableListOf<SyncFailure>()
        private val skips = mutableListOf<SyncSkip>()

        /** Roots whose upload failed, so their descendants must not be attempted. */
        private val blockedRoutines = mutableSetOf<UUID>()

        /** Routines deleted on the backend this pass; they accept no new children. */
        private val deletedRoutines = mutableSetOf<UUID>()
        private val blockedSessions = mutableSetOf<UUID>()
        private val blockedWorkoutExercises = mutableSetOf<UUID>()

        /**
         * Uploads pending routines — creates *and* deletions.
         *
         * A soft-deleted routine is not a create with a flag set; it is a
         * DELETE. Dispatching here, on the row the repository already marked
         * PENDING, is what makes deletion propagation work without a second
         * mechanism (M11 Phase 2; see ADR-0007 §Amendment).
         */
        suspend fun syncRoutines() {
            routineSource.getPendingRoutines().forEach { routine ->
                routineSource.setRoutineSyncStatus(routine.id, SyncStatus.SYNCING)
                val result = if (routine.isDeleted) {
                    attemptDelete { routineApi.deleteRoutine(routine.id.toString()) }
                } else {
                    attempt { routineApi.createRoutine(routine.toCreateRequest()) }
                }
                if (routine.isDeleted && result is UploadResult.Success) {
                    // The backend soft-deletes the routine, after which it
                    // rejects additions to it (getRoutine resolves active rows
                    // only). Any still-pending child create would therefore 404
                    // on every pass forever, so the set phase skips them.
                    deletedRoutines += routine.id
                }
                finish(
                    result = result,
                    entityType = SyncEntityType.ROUTINE,
                    id = routine.id,
                    setStatus = routineSource::setRoutineSyncStatus,
                    onFailure = { blockedRoutines += routine.id },
                )
            }
        }

        /**
         * Uploads pending routine exercises — creates *and* deletions.
         *
         * A deletion is sent even when its parent routine was just deleted: the
         * endpoint removes by id and no-ops on an unknown one, so it converges
         * either way. A *create* under a deleted routine is skipped instead,
         * because the backend would reject it permanently.
         */
        suspend fun syncRoutineExercises() {
            routineExerciseSource.getPendingRoutineExercises().forEach { routineExercise ->
                val blocker = when {
                    routineExercise.routineId in blockedRoutines -> routineExercise.routineId
                    !routineExercise.isDeleted &&
                        routineExercise.routineId in deletedRoutines -> routineExercise.routineId

                    else -> null
                }
                if (blocker != null) {
                    skip(SyncEntityType.ROUTINE_EXERCISE, routineExercise.id, blocker)
                    return@forEach
                }
                routineExerciseSource.setRoutineExerciseSyncStatus(
                    routineExercise.id,
                    SyncStatus.SYNCING,
                )
                val result = if (routineExercise.isDeleted) {
                    attemptDelete {
                        routineApi.deleteRoutineExercise(routineExercise.id.toString())
                    }
                } else {
                    attempt {
                        routineApi.addRoutineExercise(
                            routineId = routineExercise.routineId.toString(),
                            request = routineExercise.toAddRequest(),
                        )
                    }
                }
                finish(
                    result = result,
                    entityType = SyncEntityType.ROUTINE_EXERCISE,
                    id = routineExercise.id,
                    setStatus = routineExerciseSource::setRoutineExerciseSyncStatus,
                )
            }
        }

        /**
         * Uploads the *start* of every pending session.
         *
         * The row is not marked SYNCED here. A session is only fully synchronized
         * once its terminal transition has been accepted, and an IN_PROGRESS
         * workout has not reached that point yet — so it is deliberately returned
         * to PENDING and re-uploaded (idempotently) on a later pass, after the
         * user finishes it.
         */
        suspend fun syncWorkoutSessionStarts() {
            sessionSource.getPendingWorkoutSessions().forEach { session ->
                sessionSource.setWorkoutSessionSyncStatus(session.id, SyncStatus.SYNCING)
                when (val result = attempt {
                    sessionApi.startWorkoutSession(session.toStartRequest())
                }) {
                    is UploadResult.Success -> {
                        uploaded++
                        sessionSource.setWorkoutSessionSyncStatus(session.id, SyncStatus.PENDING)
                    }

                    is UploadResult.Failed -> {
                        blockedSessions += session.id
                        fail(SyncEntityType.WORKOUT_SESSION_START, session.id, result)
                        sessionSource.setWorkoutSessionSyncStatus(session.id, SyncStatus.FAILED)
                    }
                }
            }
        }

        suspend fun syncWorkoutExercises() {
            workoutExerciseSource.getPendingWorkoutExercises().forEach { exercise ->
                if (exercise.workoutSessionId in blockedSessions) {
                    // The set phase must skip this exercise's sets too: the
                    // exercise the backend would attach them to does not exist.
                    blockedWorkoutExercises += exercise.id
                    skip(
                        SyncEntityType.WORKOUT_EXERCISE,
                        exercise.id,
                        exercise.workoutSessionId,
                    )
                    return@forEach
                }
                workoutExerciseSource.setWorkoutExerciseSyncStatus(exercise.id, SyncStatus.SYNCING)
                val result = attempt {
                    logApi.addWorkoutExercise(
                        sessionId = exercise.workoutSessionId.toString(),
                        request = exercise.toAddRequest(),
                    )
                }
                finish(
                    result = result,
                    entityType = SyncEntityType.WORKOUT_EXERCISE,
                    id = exercise.id,
                    setStatus = workoutExerciseSource::setWorkoutExerciseSyncStatus,
                    onFailure = {
                        blockedWorkoutExercises += exercise.id
                        // A session whose contents are incomplete must not be
                        // sealed by a terminal transition in this pass.
                        blockedSessions += exercise.workoutSessionId
                    },
                )
            }
        }

        suspend fun syncWorkoutSets() {
            setSource.getPendingWorkoutSets().forEach { pending ->
                val set = pending.set
                val blocker = when {
                    pending.workoutSessionId in blockedSessions -> pending.workoutSessionId
                    set.workoutExerciseId in blockedWorkoutExercises -> set.workoutExerciseId
                    else -> null
                }
                if (blocker != null) {
                    skip(SyncEntityType.WORKOUT_SET, set.id, blocker)
                    return@forEach
                }
                setSource.setWorkoutSetSyncStatus(set.id, SyncStatus.SYNCING)
                val result = attempt {
                    logApi.addWorkoutSet(
                        workoutExerciseId = set.workoutExerciseId.toString(),
                        request = set.toAddRequest(),
                    )
                }
                finish(
                    result = result,
                    entityType = SyncEntityType.WORKOUT_SET,
                    id = set.id,
                    setStatus = setSource::setWorkoutSetSyncStatus,
                    onFailure = { blockedSessions += pending.workoutSessionId },
                )
            }
        }

        /**
         * Removes sets the user deleted locally (ADR-0007).
         *
         * Position in the pass is a correctness requirement, not tidiness. It
         * must run *after* the set uploads, so a set created and deleted in the
         * same offline stretch is not resurrected by a later create; and
         * *before* the terminal transition, because the backend refuses every
         * write — deletions included — to a session it has already sealed.
         *
         * A tombstone has no status column: success drops the row, and failure
         * leaves it for the next pass. The one exception is a rejection. A 4xx
         * means an unchanged retry fails identically, so keeping the tombstone
         * would retry it on every pass forever; it is dropped and reported
         * instead. The backend's DELETE is a no-op when the id is unknown, so
         * the common "never uploaded in the first place" case succeeds rather
         * than 404ing.
         */
        suspend fun syncWorkoutSetDeletions() {
            deletionSource.getPendingWorkoutSetDeletions().forEach { tombstone ->
                if (tombstone.workoutSessionId in blockedSessions) {
                    skip(
                        SyncEntityType.WORKOUT_SET_DELETION,
                        tombstone.workoutSetId,
                        tombstone.workoutSessionId,
                    )
                    return@forEach
                }
                when (val result = attempt {
                    logApi.deleteWorkoutSet(tombstone.workoutSetId.toString())
                }) {
                    is UploadResult.Success -> {
                        uploaded++
                        deletionSource.clearWorkoutSetDeletion(tombstone.workoutSetId)
                    }

                    is UploadResult.Failed -> {
                        fail(SyncEntityType.WORKOUT_SET_DELETION, tombstone.workoutSetId, result)
                        if (result.reason == SyncFailureReason.REJECTED) {
                            deletionSource.clearWorkoutSetDeletion(tombstone.workoutSetId)
                        } else {
                            // Retryable: the session must not be sealed while one
                            // of its sets is still pending removal.
                            blockedSessions += tombstone.workoutSessionId
                        }
                    }
                }
            }
        }

        /**
         * Replays workout-exercise removals (TD-014).
         *
         * Ordered deliberately between the set deletions and the terminal
         * transition. After the set deletions, because the backend refuses to
         * remove an exercise that still has sets attached, and those sets are
         * removed by the phase before this one. Before the transition, because a
         * sealed session rejects every write, deletions included.
         *
         * Failure handling matches the set tombstones: success drops the row, a
         * rejection drops it too (an unchanged retry fails identically forever),
         * and anything retryable keeps it and blocks the session from being
         * sealed while one of its exercises is still pending removal.
         */
        suspend fun syncWorkoutExerciseDeletions() {
            exerciseDeletionSource.getPendingWorkoutExerciseDeletions().forEach { tombstone ->
                if (tombstone.workoutSessionId in blockedSessions) {
                    skip(
                        SyncEntityType.WORKOUT_EXERCISE_DELETION,
                        tombstone.workoutExerciseId,
                        tombstone.workoutSessionId,
                    )
                    return@forEach
                }
                when (val result = attempt {
                    logApi.deleteWorkoutExercise(tombstone.workoutExerciseId.toString())
                }) {
                    is UploadResult.Success -> {
                        uploaded++
                        exerciseDeletionSource
                            .clearWorkoutExerciseDeletion(tombstone.workoutExerciseId)
                    }

                    is UploadResult.Failed -> {
                        fail(
                            SyncEntityType.WORKOUT_EXERCISE_DELETION,
                            tombstone.workoutExerciseId,
                            result,
                        )
                        if (result.reason == SyncFailureReason.REJECTED) {
                            exerciseDeletionSource
                                .clearWorkoutExerciseDeletion(tombstone.workoutExerciseId)
                        } else {
                            blockedSessions += tombstone.workoutSessionId
                        }
                    }
                }
            }
        }

        /**
         * Seals finished workouts. Runs last, after every child of every session
         * has been uploaded, because the backend refuses further writes to a
         * session that is no longer IN_PROGRESS.
         */
        suspend fun syncWorkoutSessionTransitions() {
            sessionSource.getPendingWorkoutSessions().forEach { session ->
                // Still being logged — there is no transition to send yet.
                if (session.status == WorkoutStatus.IN_PROGRESS) return@forEach

                if (session.id in blockedSessions) {
                    skip(SyncEntityType.WORKOUT_SESSION_TRANSITION, session.id, session.id)
                    return@forEach
                }
                sessionSource.setWorkoutSessionSyncStatus(session.id, SyncStatus.SYNCING)
                val result = attempt {
                    val id = session.id.toString()
                    when (session.status) {
                        WorkoutStatus.COMPLETED ->
                            sessionApi.completeWorkoutSession(id, session.toCompleteRequest())

                        WorkoutStatus.DISCARDED ->
                            sessionApi.discardWorkoutSession(id, session.toDiscardRequest())

                        WorkoutStatus.IN_PROGRESS ->
                            error("Unreachable: IN_PROGRESS sessions are filtered out above.")
                    }
                }
                finish(
                    result = result,
                    entityType = SyncEntityType.WORKOUT_SESSION_TRANSITION,
                    id = session.id,
                    setStatus = sessionSource::setWorkoutSessionSyncStatus,
                )
            }
        }

        /** Applies the terminal status and records the outcome of one upload. */
        private suspend fun finish(
            result: UploadResult,
            entityType: SyncEntityType,
            id: UUID,
            setStatus: suspend (UUID, SyncStatus) -> Unit,
            onFailure: () -> Unit = {},
        ) {
            when (result) {
                is UploadResult.Success -> {
                    uploaded++
                    setStatus(id, SyncStatus.SYNCED)
                }

                is UploadResult.Failed -> {
                    onFailure()
                    fail(entityType, id, result)
                    setStatus(id, SyncStatus.FAILED)
                }
            }
        }

        private fun fail(entityType: SyncEntityType, id: UUID, result: UploadResult.Failed) {
            failures += SyncFailure(entityType, id, result.reason, result.message)
        }

        private fun skip(entityType: SyncEntityType, id: UUID, blockedBy: UUID) {
            skips += SyncSkip(entityType, id, blockedBy)
        }

        fun toResult(): SyncResult {
            val summary = SyncSummary(uploaded = uploaded, failures = failures, skips = skips)
            return when {
                summary.isEmpty -> SyncResult.NothingToSync
                summary.failed == 0 && summary.skipped == 0 -> SyncResult.Success(summary)
                summary.uploaded == 0 -> SyncResult.Failure(summary)
                else -> SyncResult.Partial(summary)
            }
        }
    }

    /** The outcome of a single HTTP call, with transport errors already tamed. */
    private sealed interface UploadResult {
        data object Success : UploadResult

        /** [status] is null when the request never produced a response. */
        data class Failed(
            val reason: SyncFailureReason,
            val message: String,
            val status: Int? = null,
        ) : UploadResult
    }

    /**
     * Runs one upload, converting every way it can go wrong into an
     * [UploadResult].
     *
     * [IOException] covers the offline case, which is expected rather than
     * exceptional in an offline-first app.
     *
     * [SerializationException] is caught for a subtler reason: the engine never
     * reads a response body, but Retrofit still parses one, so a backend that
     * changed a response shape could throw *after* having successfully accepted
     * the upload. Letting that escape would abort the entire pass — including
     * aggregates that had nothing to do with it. It is treated as retryable
     * because the write itself very likely landed, and the replay will be
     * idempotent.
     *
     * Any other exception is left to propagate: a programming error is not a
     * sync failure, and silently marking rows FAILED would bury it.
     */
    private suspend fun attempt(call: suspend () -> Response<*>): UploadResult =
        try {
            val response = call()
            if (response.isSuccessful) {
                // 201 Created and 200 OK are both success: 200 means the backend
                // already had this id and updated it in place, which is exactly
                // what a replayed upload should do.
                UploadResult.Success
            } else {
                UploadResult.Failed(
                    reason = reasonForStatus(response.code()),
                    message = "HTTP ${response.code()}",
                    status = response.code(),
                )
            }
        } catch (e: IOException) {
            UploadResult.Failed(
                reason = SyncFailureReason.NETWORK,
                message = e.message ?: e::class.java.simpleName,
            )
        } catch (e: SerializationException) {
            UploadResult.Failed(
                reason = SyncFailureReason.SERVER_ERROR,
                message = "Unreadable response: ${e.message ?: e::class.java.simpleName}",
            )
        }

    /**
     * Runs a DELETE, treating **404 as success**.
     *
     * DELETE is idempotent and its goal state is "this row is not on the
     * backend". A 404 means the row is already absent, which is that goal — so
     * reporting failure would be wrong, and worse, the row would stay
     * PENDING/FAILED and be retried on every pass forever. This is reachable in
     * normal use: a routine created and deleted while offline is never uploaded
     * at all, so the DELETE that follows refers to an id the backend has never
     * seen.
     *
     * The routine-exercise endpoint already no-ops on an unknown id (204), so
     * this mainly matters for routines, which 404.
     */
    private suspend fun attemptDelete(call: suspend () -> Response<*>): UploadResult =
        when (val result = attempt(call)) {
            is UploadResult.Failed ->
                if (result.status == 404) UploadResult.Success else result

            UploadResult.Success -> UploadResult.Success
        }

    private fun reasonForStatus(code: Int): SyncFailureReason = when {
        code == 408 || code == 429 -> SyncFailureReason.SERVER_ERROR
        code >= 500 -> SyncFailureReason.SERVER_ERROR
        // 4xx means the backend rejected this payload — 404 (unknown parent),
        // 409 (session no longer IN_PROGRESS), 400 (validation). Retrying an
        // unchanged request would fail identically.
        else -> SyncFailureReason.REJECTED
    }
}

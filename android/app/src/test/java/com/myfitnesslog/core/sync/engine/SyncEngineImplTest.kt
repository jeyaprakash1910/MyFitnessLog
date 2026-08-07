package com.myfitnesslog.core.sync.engine

import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.data.remote.createApi
import com.myfitnesslog.core.sync.model.SyncEntityType
import com.myfitnesslog.core.sync.model.SyncFailureReason
import com.myfitnesslog.core.sync.model.SyncResult
import com.myfitnesslog.core.sync.testing.FakeRoutineExerciseSyncSource
import com.myfitnesslog.core.sync.testing.FakeRoutineSyncSource
import com.myfitnesslog.core.sync.testing.FakeWorkoutExerciseSyncSource
import com.myfitnesslog.core.sync.testing.FakeWorkoutSessionSyncSource
import com.myfitnesslog.core.sync.testing.FakeWorkoutExerciseDeletionSyncSource
import com.myfitnesslog.core.sync.testing.FakeWorkoutSetDeletionSyncSource
import com.myfitnesslog.core.sync.testing.FakeWorkoutSetSyncSource
import com.myfitnesslog.core.sync.testing.SyncEntityFixtures
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutLogApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.UUID
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseTombstoneEntity
import org.junit.Assert.assertFalse

/**
 * Behaviour tests for [SyncEngineImpl].
 *
 * The sources are fakes, but the HTTP layer is a real MockWebServer behind the
 * real Retrofit interfaces — so these tests exercise genuine serialization,
 * status-code handling, and request ordering rather than a mocked-out client.
 */
class SyncEngineImplTest {

    private companion object {
        /** Any syntactically valid id — the engine never reads response bodies. */
        const val STUB = "99999999-9999-4999-8999-999999999999"
    }

    private lateinit var server: MockWebServer
    private lateinit var engine: SyncEngineImpl

    private val routineSource = FakeRoutineSyncSource()
    private val routineExerciseSource = FakeRoutineExerciseSyncSource()
    private val sessionSource = FakeWorkoutSessionSyncSource()
    private val workoutExerciseSource = FakeWorkoutExerciseSyncSource()
    private val setSource = FakeWorkoutSetSyncSource()
    private val deletionSource = FakeWorkoutSetDeletionSyncSource()
    private val exerciseDeletionSource = FakeWorkoutExerciseDeletionSyncSource()

    /** Every path that was requested, in order — the ordering contract. */
    private val requestedPaths = mutableListOf<String>()

    /** Path suffix → the response to give it; anything unmatched gets 201. */
    private val overrides = mutableMapOf<String, MockResponse>()


    /**
     * A plausible response body for whichever endpoint was hit.
     *
     * The engine ignores response bodies, but Retrofit still parses them, so the
     * fake backend must answer with payloads the DTOs actually accept — a stub
     * like `{}` would exercise the parse-failure path instead of the happy one.
     */
    private fun bodyFor(path: String): String = when {
        path.endsWith("/sets") -> """{"id":"$STUB","workoutExerciseId":"$STUB","setNumber":1,
            "weight":60.00,"repetitions":10,"setCategory":"WORKING","isCompleted":true}"""

        path.contains("/workout-sessions/") && path.endsWith("/exercises") ->
            """{"id":"$STUB","workoutSessionId":"$STUB","exerciseId":"$STUB",
               "exerciseName":"Bench Press","exerciseOrder":0,"targetSets":3,
               "minTargetReps":8,"maxTargetReps":12}"""

        path.contains("/workout-sessions") ->
            """{"id":"$STUB","status":"COMPLETED","startedAt":"2026-07-21T09:00:00Z"}"""

        path.endsWith("/exercises") ->
            """{"id":"$STUB","exerciseId":"$STUB","exerciseOrder":0,"targetSets":3,
               "minTargetReps":8,"maxTargetReps":12}"""

        else -> """{"id":"$STUB","name":"Push Day","displayOrder":0}"""
    }

    private fun ok(path: String, code: Int = 201): MockResponse =
        MockResponse().setResponseCode(code).setBody(bodyFor(path))

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                synchronized(requestedPaths) { requestedPaths += "${request.method} $path" }
                overrides.entries.firstOrNull { path.endsWith(it.key) }?.let { return it.value }
                return ok(path)
            }
        }
        server.start()

        engine = SyncEngineImpl(
            routineSource = routineSource,
            routineExerciseSource = routineExerciseSource,
            sessionSource = sessionSource,
            workoutExerciseSource = workoutExerciseSource,
            setSource = setSource,
            deletionSource = deletionSource,
            exerciseDeletionSource = exerciseDeletionSource,
            routineApi = server.createApi<RoutineApi>(),
            sessionApi = server.createApi<WorkoutSessionApi>(),
            logApi = server.createApi<WorkoutLogApi>(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fail(pathSuffix: String, code: Int) {
        overrides[pathSuffix] = MockResponse().setResponseCode(code).setBody("""{"message":"error"}""")
    }

    // ---- Nothing to do ----------------------------------------------------

    @Test
    fun `an empty queue reports NothingToSync and makes no requests`() = runTest {
        val result = engine.sync()

        assertEquals(SyncResult.NothingToSync, result)
        assertEquals(0, server.requestCount)
    }

    // ---- Happy path -------------------------------------------------------

    @Test
    fun `a full routine-backed workout uploads in the canonical dependency order`() = runTest {
        val routineId = UUID.randomUUID()
        val routineExerciseId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val workoutExerciseId = UUID.randomUUID()
        val setId = UUID.randomUUID()

        routineSource.pending = listOf(SyncEntityFixtures.routine(id = routineId))
        routineExerciseSource.pending =
            listOf(SyncEntityFixtures.routineExercise(id = routineExerciseId, routineId = routineId))
        sessionSource.pending =
            listOf(SyncEntityFixtures.session(id = sessionId, routineId = routineId))
        workoutExerciseSource.pending =
            listOf(SyncEntityFixtures.workoutExercise(id = workoutExerciseId, sessionId = sessionId))
        setSource.pending = listOf(
            SyncEntityFixtures.pendingSet(
                id = setId,
                workoutExerciseId = workoutExerciseId,
                sessionId = sessionId,
            ),
        )

        val result = engine.sync()

        assertEquals(
            listOf(
                "POST /api/v1/routines",
                "POST /api/v1/routines/$routineId/exercises",
                "POST /api/v1/workout-sessions",
                "POST /api/v1/workout-sessions/$sessionId/exercises",
                "POST /api/v1/workout-exercises/$workoutExerciseId/sets",
                "PUT /api/v1/workout-sessions/$sessionId/complete",
            ),
            requestedPaths,
        )
        assertTrue(result.toString(), result is SyncResult.Success)
        // Five children + the session counted once at start and once at transition.
        assertEquals(6, result.summary.uploaded)
        assertEquals(0, result.summary.failed)
        assertEquals(0, result.summary.skipped)
    }

    @Test
    fun `a manual workout syncs with no routine phases`() = runTest {
        val sessionId = UUID.randomUUID()
        sessionSource.pending = listOf(
            SyncEntityFixtures.session(id = sessionId, routineId = null),
        )

        val result = engine.sync()

        assertEquals(
            listOf(
                "POST /api/v1/workout-sessions",
                "PUT /api/v1/workout-sessions/$sessionId/complete",
            ),
            requestedPaths,
        )
        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun `a discarded workout sends the discard transition`() = runTest {
        val sessionId = UUID.randomUUID()
        sessionSource.pending = listOf(
            SyncEntityFixtures.session(id = sessionId, status = WorkoutStatus.DISCARDED),
        )

        engine.sync()

        assertTrue(
            requestedPaths.toString(),
            requestedPaths.contains("PUT /api/v1/workout-sessions/$sessionId/discard"),
        )
    }

    // ---- Status transitions ----------------------------------------------

    @Test
    fun `a successful routine transitions PENDING to SYNCING to SYNCED`() = runTest {
        val routineId = UUID.randomUUID()
        routineSource.pending = listOf(SyncEntityFixtures.routine(id = routineId))

        engine.sync()

        assertEquals(
            listOf(SyncStatus.SYNCING, SyncStatus.SYNCED),
            routineSource.statusesFor(routineId),
        )
    }

    @Test
    fun `a failed routine transitions SYNCING then FAILED, never SYNCED`() = runTest {
        val routineId = UUID.randomUUID()
        routineSource.pending = listOf(SyncEntityFixtures.routine(id = routineId))
        fail("/routines", 500)

        engine.sync()

        assertEquals(
            listOf(SyncStatus.SYNCING, SyncStatus.FAILED),
            routineSource.statusesFor(routineId),
        )
    }

    @Test
    fun `a previously FAILED row is retried on the next pass`() = runTest {
        // The pending query returns PENDING and FAILED alike; the engine must
        // treat a FAILED row as ordinary work rather than skipping it.
        val routineId = UUID.randomUUID()
        routineSource.pending =
            listOf(SyncEntityFixtures.routine(id = routineId, syncStatus = SyncStatus.FAILED))

        val result = engine.sync()

        assertEquals(listOf("POST /api/v1/routines"), requestedPaths)
        assertEquals(
            listOf(SyncStatus.SYNCING, SyncStatus.SYNCED),
            routineSource.statusesFor(routineId),
        )
        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun `an in-progress session is returned to PENDING and not sealed`() = runTest {
        val sessionId = UUID.randomUUID()
        sessionSource.pending = listOf(
            SyncEntityFixtures.session(
                id = sessionId,
                status = WorkoutStatus.IN_PROGRESS,
                endedAt = null,
            ),
        )

        val result = engine.sync()

        assertEquals(listOf("POST /api/v1/workout-sessions"), requestedPaths)
        // Not SYNCED: the workout is not finished, so it must be revisited.
        assertEquals(
            listOf(SyncStatus.SYNCING, SyncStatus.PENDING),
            sessionSource.statusesFor(sessionId),
        )
        assertTrue(result is SyncResult.Success)
    }

    // ---- Idempotent replay ------------------------------------------------

    @Test
    fun `a 200 OK replay is treated as success exactly like 201`() = runTest {
        val routineId = UUID.randomUUID()
        routineSource.pending = listOf(SyncEntityFixtures.routine(id = routineId))
        overrides["/routines"] = MockResponse().setResponseCode(200).setBody(bodyFor("/routines"))

        val result = engine.sync()

        assertTrue(result is SyncResult.Success)
        assertEquals(
            listOf(SyncStatus.SYNCING, SyncStatus.SYNCED),
            routineSource.statusesFor(routineId),
        )
    }

    // ---- Partial failure and aggregate isolation --------------------------

    @Test
    fun `a failed routine blocks its own exercises but not another routine`() = runTest {
        val failingId = UUID.randomUUID()
        val healthyId = UUID.randomUUID()
        val blockedChild = UUID.randomUUID()
        val healthyChild = UUID.randomUUID()

        routineSource.pending = listOf(
            SyncEntityFixtures.routine(id = failingId),
            SyncEntityFixtures.routine(id = healthyId),
        )
        routineExerciseSource.pending = listOf(
            SyncEntityFixtures.routineExercise(id = blockedChild, routineId = failingId),
            SyncEntityFixtures.routineExercise(id = healthyChild, routineId = healthyId),
        )
        // Only the first routine POST fails; MockWebServer matches on suffix, so
        // discriminate by responding to the shared path conditionally.
        var routinePosts = 0
        overrides["/routines"] = MockResponse().setResponseCode(500)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths += "${request.method} $path"
                if (path.endsWith("/routines")) {
                    routinePosts++
                    if (routinePosts == 1) return MockResponse().setResponseCode(500).setBody("{}")
                }
                return ok(path)
            }
        }

        val result = engine.sync()

        assertTrue(result.toString(), result is SyncResult.Partial)
        // The healthy routine and its child both went up.
        assertEquals(2, result.summary.uploaded)
        assertEquals(1, result.summary.failed)
        assertEquals(1, result.summary.skipped)

        // The blocked child was never attempted, and its status was left alone
        // so the next pass picks it up unchanged.
        assertTrue(
            requestedPaths.none { it.contains("/routines/$failingId/exercises") },
        )
        assertEquals(emptyList<SyncStatus>(), routineExerciseSource.statusesFor(blockedChild))
        assertEquals(
            listOf(SyncStatus.SYNCING, SyncStatus.SYNCED),
            routineExerciseSource.statusesFor(healthyChild),
        )
        assertEquals(blockedChild, result.summary.skips.single().id)
        assertEquals(failingId, result.summary.skips.single().blockedBy)
    }

    @Test
    fun `a failed session start skips its children and its transition`() = runTest {
        val sessionId = UUID.randomUUID()
        val exerciseId = UUID.randomUUID()
        val setId = UUID.randomUUID()

        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId))
        workoutExerciseSource.pending =
            listOf(SyncEntityFixtures.workoutExercise(id = exerciseId, sessionId = sessionId))
        setSource.pending = listOf(
            SyncEntityFixtures.pendingSet(
                id = setId,
                workoutExerciseId = exerciseId,
                sessionId = sessionId,
            ),
        )
        fail("/workout-sessions", 503)

        val result = engine.sync()

        assertEquals(listOf("POST /api/v1/workout-sessions"), requestedPaths)
        assertTrue(result.toString(), result is SyncResult.Failure)
        assertEquals(0, result.summary.uploaded)
        // The exercise, the set, and the terminal transition were all skipped.
        assertEquals(3, result.summary.skipped)
        assertEquals(emptyList<SyncStatus>(), workoutExerciseSource.statusesFor(exerciseId))
        assertEquals(emptyList<SyncStatus>(), setSource.statusesFor(setId))
    }

    @Test
    fun `a failed set prevents its session from being sealed`() = runTest {
        // Sealing a session whose sets are missing would make those sets
        // permanently unwritable — the backend rejects changes once a session
        // leaves IN_PROGRESS.
        val sessionId = UUID.randomUUID()
        val exerciseId = UUID.randomUUID()
        val setId = UUID.randomUUID()

        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId))
        workoutExerciseSource.pending =
            listOf(SyncEntityFixtures.workoutExercise(id = exerciseId, sessionId = sessionId))
        setSource.pending = listOf(
            SyncEntityFixtures.pendingSet(
                id = setId,
                workoutExerciseId = exerciseId,
                sessionId = sessionId,
            ),
        )
        fail("/sets", 500)

        val result = engine.sync()

        assertTrue(requestedPaths.none { it.contains("/complete") })
        assertTrue(result is SyncResult.Partial)
        assertEquals(
            SyncEntityType.WORKOUT_SESSION_TRANSITION,
            result.summary.skips.single().entityType,
        )
    }

    @Test
    fun `two independent sessions do not block each other`() = runTest {
        val failing = UUID.randomUUID()
        val healthy = UUID.randomUUID()
        sessionSource.pending = listOf(
            SyncEntityFixtures.session(id = failing),
            SyncEntityFixtures.session(id = healthy),
        )
        var starts = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths += "${request.method} $path"
                if (path.endsWith("/workout-sessions")) {
                    starts++
                    if (starts == 1) return MockResponse().setResponseCode(500).setBody("{}")
                }
                return ok(path)
            }
        }

        val result = engine.sync()

        assertTrue(result is SyncResult.Partial)
        assertTrue(requestedPaths.contains("PUT /api/v1/workout-sessions/$healthy/complete"))
        assertTrue(requestedPaths.none { it.contains("$failing/complete") })
    }

    // ---- Failure classification ------------------------------------------

    @Test
    fun `a 404 is classified as a permanent rejection`() = runTest {
        routineSource.pending = listOf(SyncEntityFixtures.routine())
        fail("/routines", 404)

        val result = engine.sync()

        val failure = result.summary.failures.single()
        assertEquals(SyncFailureReason.REJECTED, failure.reason)
        assertEquals(false, failure.reason.isRetryable)
        assertEquals(false, result.summary.hasRetryableFailure)
    }

    @Test
    fun `a 409 conflict is classified as a permanent rejection`() = runTest {
        val sessionId = UUID.randomUUID()
        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId))
        fail("/complete", 409)

        val result = engine.sync()

        val failure = result.summary.failures.single()
        assertEquals(SyncEntityType.WORKOUT_SESSION_TRANSITION, failure.entityType)
        assertEquals(SyncFailureReason.REJECTED, failure.reason)
        assertEquals("HTTP 409", failure.message)
    }

    @Test
    fun `5xx and 429 are classified as retryable server errors`() = runTest {
        routineSource.pending = listOf(SyncEntityFixtures.routine())
        fail("/routines", 429)

        val result = engine.sync()

        assertEquals(SyncFailureReason.SERVER_ERROR, result.summary.failures.single().reason)
        assertTrue(result.summary.hasRetryableFailure)
    }

    @Test
    fun `being offline is classified as a retryable network failure`() = runTest {
        routineSource.pending = listOf(SyncEntityFixtures.routine())
        // Shutting the server down refuses the connection outright, which is the
        // closest faithful stand-in for having no network at all.
        server.shutdown()

        val result = engine.sync()

        assertTrue(result.toString(), result is SyncResult.Failure)
        assertEquals(SyncFailureReason.NETWORK, result.summary.failures.single().reason)
        assertTrue(result.summary.hasRetryableFailure)
    }

    // ---- Progress preservation -------------------------------------------

    @Test
    fun `progress already made is never discarded when a later phase fails`() = runTest {
        val routineId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        routineSource.pending = listOf(SyncEntityFixtures.routine(id = routineId))
        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId))
        fail("/workout-sessions", 500)

        val result = engine.sync()

        // The routine uploaded before the workout phase failed, and stays SYNCED.
        assertEquals(
            listOf(SyncStatus.SYNCING, SyncStatus.SYNCED),
            routineSource.statusesFor(routineId),
        )
        assertTrue(result is SyncResult.Partial)
        assertEquals(1, result.summary.uploaded)
    }
    @Test
    fun `an unreadable success body degrades to a retryable failure, not a crash`() = runTest {
        // The engine ignores response bodies, but Retrofit parses them. A backend
        // that changed a response shape must not be able to abort the whole pass.
        val routineId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        routineSource.pending = listOf(SyncEntityFixtures.routine(id = routineId))
        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId))
        overrides["/routines"] = MockResponse().setResponseCode(201).setBody("""{"unexpected":1}""")

        val result = engine.sync()

        val failure = result.summary.failures.single()
        assertEquals(SyncEntityType.ROUTINE, failure.entityType)
        assertEquals(SyncFailureReason.SERVER_ERROR, failure.reason)
        assertTrue(failure.message, failure.message.startsWith("Unreadable response"))
        // The unrelated workout still synced.
        assertTrue(requestedPaths.contains("POST /api/v1/workout-sessions"))
        assertTrue(result is SyncResult.Partial)
    }

    // ---- Routine and routine-exercise deletions (M11 Phase 2, D-1) --------

    @Test
    fun `a soft-deleted routine is sent as a DELETE, not re-created`() = runTest {
        val routineId = UUID.randomUUID()
        routineSource.pending = listOf(
            SyncEntityFixtures.routine(id = routineId, isDeleted = true),
        )

        val result = engine.sync()

        assertEquals(listOf("DELETE /api/v1/routines/$routineId"), requestedPaths)
        assertEquals(listOf(SyncStatus.SYNCING, SyncStatus.SYNCED), routineSource.statusesFor(routineId))
        assertTrue(result.toString(), result is SyncResult.Success)
    }

    @Test
    fun `a soft-deleted routine exercise is sent as a DELETE`() = runTest {
        val routineId = UUID.randomUUID()
        val exerciseId = UUID.randomUUID()
        routineSource.pending = listOf(SyncEntityFixtures.routine(id = routineId))
        routineExerciseSource.pending = listOf(
            SyncEntityFixtures.routineExercise(id = exerciseId, routineId = routineId)
                .copy(isDeleted = true),
        )

        engine.sync()

        assertTrue(
            requestedPaths.toString(),
            requestedPaths.contains("DELETE /api/v1/routine-exercises/$exerciseId"),
        )
        assertTrue(requestedPaths.none { it.contains("/routines/$routineId/exercises") })
    }

    @Test
    fun `deleting something the backend never received counts as success`() = runTest {
        // A routine created and deleted while offline is never uploaded, so the
        // DELETE names an id the backend has never seen. Treating that 404 as a
        // failure would leave the row pending and retry it on every pass forever.
        val routineId = UUID.randomUUID()
        routineSource.pending = listOf(
            SyncEntityFixtures.routine(id = routineId, isDeleted = true),
        )
        fail("/routines/$routineId", 404)

        val result = engine.sync()

        assertEquals(listOf(SyncStatus.SYNCING, SyncStatus.SYNCED), routineSource.statusesFor(routineId))
        assertEquals(0, result.summary.failed)
        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun `a genuinely failed deletion is retried, not swallowed`() = runTest {
        val routineId = UUID.randomUUID()
        routineSource.pending = listOf(
            SyncEntityFixtures.routine(id = routineId, isDeleted = true),
        )
        fail("/routines/$routineId", 503)

        val result = engine.sync()

        assertEquals(listOf(SyncStatus.SYNCING, SyncStatus.FAILED), routineSource.statusesFor(routineId))
        assertEquals(SyncFailureReason.SERVER_ERROR, result.summary.failures.single().reason)
    }

    @Test
    fun `a pending child create is skipped once its routine is deleted`() = runTest {
        // The backend resolves a routine's active row before adding to it, so a
        // create under a deleted routine would 404 on every pass forever.
        val routineId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        routineSource.pending = listOf(
            SyncEntityFixtures.routine(id = routineId, isDeleted = true),
        )
        routineExerciseSource.pending = listOf(
            SyncEntityFixtures.routineExercise(id = childId, routineId = routineId),
        )

        val result = engine.sync()

        assertTrue(requestedPaths.none { it.contains("/exercises") })
        val skip = result.summary.skips.single { it.entityType == SyncEntityType.ROUTINE_EXERCISE }
        assertEquals(childId, skip.id)
    }

    @Test
    fun `a child deletion still uploads even when its routine was deleted`() = runTest {
        // Deleting by id converges regardless of the parent, and the endpoint
        // no-ops on an unknown id.
        val routineId = UUID.randomUUID()
        val childId = UUID.randomUUID()
        routineSource.pending = listOf(
            SyncEntityFixtures.routine(id = routineId, isDeleted = true),
        )
        routineExerciseSource.pending = listOf(
            SyncEntityFixtures.routineExercise(id = childId, routineId = routineId)
                .copy(isDeleted = true),
        )

        engine.sync()

        assertTrue(
            requestedPaths.toString(),
            requestedPaths.contains("DELETE /api/v1/routine-exercises/$childId"),
        )
    }

    // ---- Exercise deletions (TD-014) -------------------------------------

    @Test
    fun `an exercise deletion is sent after its sets are removed and before sealing`() = runTest {
        // The ordering is what makes this work at all. The backend refuses to
        // remove an exercise that still has sets, so the set deletions must land
        // first; and it refuses every write to a sealed session, so both must land
        // before the transition. Getting either wrong leaves the exercise on the
        // backend, which is the whole of TD-014.
        val sessionId = UUID.randomUUID()
        val deletedExerciseId = UUID.randomUUID()
        val deletedSetId = UUID.randomUUID()

        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId, routineId = null))
        deletionSource.pending = listOf(
            SyncEntityFixtures.setTombstone(
                workoutSetId = deletedSetId,
                workoutExerciseId = deletedExerciseId,
                sessionId = sessionId,
            ),
        )
        exerciseDeletionSource.pending = listOf(
            WorkoutExerciseTombstoneEntity(
                workoutExerciseId = deletedExerciseId,
                workoutSessionId = sessionId,
                deletedAt = Instant.parse("2026-08-07T10:00:00Z"),
            ),
        )

        val result = engine.sync()

        assertEquals(
            listOf(
                "POST /api/v1/workout-sessions",
                "DELETE /api/v1/workout-sets/$deletedSetId",
                "DELETE /api/v1/workout-exercises/$deletedExerciseId",
                "PUT /api/v1/workout-sessions/$sessionId/complete",
            ),
            requestedPaths,
        )
        assertEquals(listOf(deletedExerciseId), exerciseDeletionSource.cleared)
        assertTrue(result.toString(), result is SyncResult.Success)
    }

    @Test
    fun `a retryable exercise-deletion failure keeps the tombstone and blocks sealing`() = runTest {
        // Sealing the session while an exercise is still pending removal would
        // strand it on the backend permanently: a sealed session rejects every
        // later write, so the deletion could never be replayed.
        val sessionId = UUID.randomUUID()
        val deletedExerciseId = UUID.randomUUID()

        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId, routineId = null))
        exerciseDeletionSource.pending = listOf(
            WorkoutExerciseTombstoneEntity(
                workoutExerciseId = deletedExerciseId,
                workoutSessionId = sessionId,
                deletedAt = Instant.parse("2026-08-07T10:00:00Z"),
            ),
        )
        overrides["/api/v1/workout-exercises/$deletedExerciseId"] =
            MockResponse().setResponseCode(500)

        engine.sync()

        assertTrue(
            "tombstone must survive a retryable failure",
            exerciseDeletionSource.cleared.isEmpty(),
        )
        assertFalse(
            "session must not be sealed while a deletion is outstanding",
            requestedPaths.any { it.endsWith("/complete") },
        )
    }

    @Test
    fun `a rejected exercise deletion drops the tombstone instead of retrying forever`() = runTest {
        // A 4xx means an unchanged retry fails identically. Keeping the tombstone
        // would re-send the same doomed request on every pass for the life of the
        // install.
        val sessionId = UUID.randomUUID()
        val deletedExerciseId = UUID.randomUUID()

        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId, routineId = null))
        exerciseDeletionSource.pending = listOf(
            WorkoutExerciseTombstoneEntity(
                workoutExerciseId = deletedExerciseId,
                workoutSessionId = sessionId,
                deletedAt = Instant.parse("2026-08-07T10:00:00Z"),
            ),
        )
        overrides["/api/v1/workout-exercises/$deletedExerciseId"] =
            MockResponse().setResponseCode(400)

        engine.sync()

        assertEquals(listOf(deletedExerciseId), exerciseDeletionSource.cleared)
    }

    // ---- Set deletions (ADR-0007) ----------------------------------------

    @Test
    fun `a deletion is sent after the set uploads and before the session is sealed`() = runTest {
        // Ordering is the correctness property: after the creates, so a set
        // created and deleted offline is not resurrected; before the transition,
        // because the backend refuses writes to a sealed session.
        val sessionId = UUID.randomUUID()
        val workoutExerciseId = UUID.randomUUID()
        val keptSetId = UUID.randomUUID()
        val deletedSetId = UUID.randomUUID()

        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId, routineId = null))
        workoutExerciseSource.pending =
            listOf(SyncEntityFixtures.workoutExercise(id = workoutExerciseId, sessionId = sessionId))
        setSource.pending = listOf(
            SyncEntityFixtures.pendingSet(
                id = keptSetId,
                workoutExerciseId = workoutExerciseId,
                sessionId = sessionId,
            ),
        )
        deletionSource.pending = listOf(
            SyncEntityFixtures.setTombstone(
                workoutSetId = deletedSetId,
                workoutExerciseId = workoutExerciseId,
                sessionId = sessionId,
            ),
        )

        val result = engine.sync()

        assertEquals(
            listOf(
                "POST /api/v1/workout-sessions",
                "POST /api/v1/workout-sessions/$sessionId/exercises",
                "POST /api/v1/workout-exercises/$workoutExerciseId/sets",
                "DELETE /api/v1/workout-sets/$deletedSetId",
                "PUT /api/v1/workout-sessions/$sessionId/complete",
            ),
            requestedPaths,
        )
        assertEquals(listOf(deletedSetId), deletionSource.cleared)
        assertTrue(result.toString(), result is SyncResult.Success)
    }

    @Test
    fun `a retryable deletion failure keeps the tombstone and blocks the transition`() = runTest {
        // A workout must never be sealed while one of its sets is still pending
        // removal — sealing it would lock the deletion out permanently.
        val sessionId = UUID.randomUUID()
        val deletedSetId = UUID.randomUUID()
        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId, routineId = null))
        deletionSource.pending = listOf(
            SyncEntityFixtures.setTombstone(workoutSetId = deletedSetId, sessionId = sessionId),
        )
        fail("/workout-sets/$deletedSetId", 503)

        val result = engine.sync()

        assertTrue(deletionSource.cleared.isEmpty())
        assertTrue(requestedPaths.toString(), requestedPaths.none { it.contains("/complete") })
        val failure = result.summary.failures.single()
        assertEquals(SyncEntityType.WORKOUT_SET_DELETION, failure.entityType)
        assertEquals(SyncFailureReason.SERVER_ERROR, failure.reason)
    }

    @Test
    fun `a rejected deletion drops the tombstone instead of retrying forever`() = runTest {
        // An unchanged request rejected with 4xx will fail identically on every
        // future pass; keeping the row would poison the queue permanently.
        val sessionId = UUID.randomUUID()
        val deletedSetId = UUID.randomUUID()
        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId, routineId = null))
        deletionSource.pending = listOf(
            SyncEntityFixtures.setTombstone(workoutSetId = deletedSetId, sessionId = sessionId),
        )
        fail("/workout-sets/$deletedSetId", 409)

        val result = engine.sync()

        assertEquals(listOf(deletedSetId), deletionSource.cleared)
        assertEquals(
            SyncFailureReason.REJECTED,
            result.summary.failures.single().reason,
        )
        // The session is not blocked: nothing is left pending for it.
        assertTrue(requestedPaths.contains("PUT /api/v1/workout-sessions/$sessionId/complete"))
    }

    @Test
    fun `a deletion whose session failed to upload is skipped, not attempted`() = runTest {
        val sessionId = UUID.randomUUID()
        val deletedSetId = UUID.randomUUID()
        sessionSource.pending = listOf(SyncEntityFixtures.session(id = sessionId, routineId = null))
        deletionSource.pending = listOf(
            SyncEntityFixtures.setTombstone(workoutSetId = deletedSetId, sessionId = sessionId),
        )
        fail("/workout-sessions", 500)

        val result = engine.sync()

        assertTrue(requestedPaths.none { it.startsWith("DELETE") })
        assertTrue(deletionSource.cleared.isEmpty())
        // The session's own transition is skipped too, hence the filter.
        val skip = result.summary.skips
            .single { it.entityType == SyncEntityType.WORKOUT_SET_DELETION }
        assertEquals(deletedSetId, skip.id)
        assertEquals(sessionId, skip.blockedBy)
    }
}

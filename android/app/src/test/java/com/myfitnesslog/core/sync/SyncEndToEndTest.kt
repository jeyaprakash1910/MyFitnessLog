package com.myfitnesslog.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.remote.createApi
import com.myfitnesslog.core.sync.engine.SyncEngineImpl
import com.myfitnesslog.core.sync.engine.SyncRecoveryImpl
import com.myfitnesslog.core.sync.model.SyncResult
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.data.remote.WorkoutLogApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Application-level synchronization walkthrough.
 *
 * Everything below the scheduler is real: Room, the repositories, the mappers,
 * Retrofit, and the engine. Only the backend is a stand-in (MockWebServer) and
 * only the scheduler is a recorder — WorkManager's own behaviour is covered by
 * [com.myfitnesslog.core.sync.work.SyncSchedulerImplTest].
 *
 * These are the tests that would catch a break in the seams *between* the pieces
 * that the per-component tests each verify in isolation.
 */
@RunWith(RobolectricTestRunner::class)
class SyncEndToEndTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var server: MockWebServer
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase
    private lateinit var engine: SyncEngineImpl
    private lateinit var recovery: SyncRecoveryImpl

    private val trigger = RecordingSyncTrigger()
    private val clock = Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC)

    private val categoryId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()

    private val requestedPaths = mutableListOf<String>()
    private val overrides = mutableMapOf<String, MockResponse>()

    private companion object {
        const val STUB = "99999999-9999-4999-8999-999999999999"
    }

    /** Plausible response bodies, so real deserialization runs on the happy path. */
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

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        database.exerciseCategoryDao()
            .upsert(ExerciseCategoryEntity(id = categoryId, name = "Chest"))
        database.exerciseDao().upsert(
            ExerciseEntity(id = exerciseId, categoryId = categoryId, name = "Bench Press"),
        )

        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths += "${request.method} $path"
                overrides.entries.firstOrNull { path.endsWith(it.key) }?.let { return it.value }
                return MockResponse().setResponseCode(201).setBody(bodyFor(path))
            }
        }
        server.start()

        val dispatcher = UnconfinedTestDispatcher()
        routineRepository = RoutineRepositoryImpl(
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            ioDispatcher = dispatcher,
            clock = clock,
            syncTrigger = trigger,
        )
        workoutRepository = WorkoutRepositoryImpl(
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = dispatcher,
            clock = clock,
            syncTrigger = trigger,
        )
        startWorkout = StartWorkoutUseCase(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            routineRepository = routineRepository,
            clock = clock,
            ioDispatcher = dispatcher,
            syncTrigger = trigger,
        )
        engine = SyncEngineImpl(
            routineSource = routineRepository,
            routineExerciseSource = routineRepository,
            sessionSource = workoutRepository,
            workoutExerciseSource = workoutRepository,
            setSource = workoutRepository,
            deletionSource = workoutRepository,
            routineApi = server.createApi<RoutineApi>(),
            sessionApi = server.createApi<WorkoutSessionApi>(),
            logApi = server.createApi<WorkoutLogApi>(),
            ioDispatcher = dispatcher,
        )
        recovery = SyncRecoveryImpl(
            routineSource = routineRepository,
            workoutSource = workoutRepository,
            ioDispatcher = dispatcher,
        )
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    /**
     * The user journey the whole milestone exists to support: build a routine,
     * train it, finish. Returns the ids involved.
     */
    private suspend fun performFullWorkout(): Ids {
        val routineId = routineRepository.createRoutine("Push Day")
        val routineExerciseId = routineRepository.addExercise(
            routineId = routineId,
            exerciseId = exerciseId,
            targetSets = 3,
            minTargetReps = 8,
            maxTargetReps = 12,
            targetRestSeconds = 90,
            notes = null,
        )
        val sessionId = startWorkout(routineId)
        // The routine's exercise was snapshotted by the use case.
        val workoutExerciseId = database.workoutExerciseDao().getBySession(sessionId).single().id
        val setId = workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("60.00"),
            repetitions = 10,
            setCategory = SetCategory.WORKING,
            rpe = null,
            rir = null,
        )
        workoutRepository.completeWorkout(sessionId)
        return Ids(routineId, routineExerciseId, sessionId, workoutExerciseId, setId)
    }

    private data class Ids(
        val routineId: UUID,
        val routineExerciseId: UUID,
        val sessionId: UUID,
        val workoutExerciseId: UUID,
        val setId: UUID,
    )

    private suspend fun statusOf(ids: Ids) = listOf(
        database.routineDao().getById(ids.routineId)!!.syncStatus,
        database.routineExerciseDao().getById(ids.routineExerciseId)!!.syncStatus,
        database.workoutSessionDao().getById(ids.sessionId)!!.syncStatus,
        database.workoutExerciseDao().getById(ids.workoutExerciseId)!!.syncStatus,
        database.workoutSetDao().getById(ids.setId)!!.syncStatus,
    )

    // ---- The walkthrough --------------------------------------------------

    @Test
    fun `a full workout uploads in the canonical order and lands fully SYNCED`() = runTest {
        val ids = performFullWorkout()
        requestedPaths.clear()

        val result = engine.sync()

        assertEquals(
            listOf(
                "POST /api/v1/routines",
                "POST /api/v1/routines/${ids.routineId}/exercises",
                "POST /api/v1/workout-sessions",
                "POST /api/v1/workout-sessions/${ids.sessionId}/exercises",
                "POST /api/v1/workout-exercises/${ids.workoutExerciseId}/sets",
                "PUT /api/v1/workout-sessions/${ids.sessionId}/complete",
            ),
            requestedPaths,
        )
        assertTrue(result.toString(), result is SyncResult.Success)
        assertEquals(List(5) { SyncStatus.SYNCED }, statusOf(ids))
    }

    @Test
    fun `the set's decimal weight survives the whole stack unchanged`() = runTest {
        performFullWorkout()
        requestedPaths.clear()

        engine.sync()

        // Room BigDecimal → entity → DTO → JSON, asserted on the literal wire text.
        val setRequest = server.takeRequestMatching("/sets")
        assertTrue(setRequest, setRequest.contains(""""weight":60.00"""))
    }

    /** Drains recorded requests until one for [pathSuffix] is found; returns its body. */
    private fun MockWebServer.takeRequestMatching(pathSuffix: String): String {
        repeat(requestCount) {
            val recorded = takeRequest()
            if (recorded.path.orEmpty().endsWith(pathSuffix)) return recorded.body.readUtf8()
        }
        error("No request matching $pathSuffix")
    }

    @Test
    fun `a second pass after a successful one has nothing to do`() = runTest {
        performFullWorkout()
        engine.sync()
        requestedPaths.clear()

        val result = engine.sync()

        assertEquals(SyncResult.NothingToSync, result)
        assertEquals(emptyList<String>(), requestedPaths)
    }

    @Test
    fun `replaying against a backend that already has the data still converges`() = runTest {
        // Every create answers 200 OK — the backend's idempotent "already had
        // this id" signal, which is what a retried-after-lost-response pass sees.
        val ids = performFullWorkout()
        overrides["/routines"] = MockResponse().setResponseCode(200).setBody(bodyFor("/routines"))
        overrides["/workout-sessions"] =
            MockResponse().setResponseCode(200).setBody(bodyFor("/workout-sessions"))

        val result = engine.sync()

        assertTrue(result.toString(), result is SyncResult.Success)
        assertEquals(List(5) { SyncStatus.SYNCED }, statusOf(ids))
    }

    // ---- Failure, retry, recovery ----------------------------------------

    @Test
    fun `an offline pass leaves everything FAILED and loses no data`() = runTest {
        val ids = performFullWorkout()
        server.shutdown()

        val result = engine.sync()

        assertTrue(result.toString(), result is SyncResult.Failure)
        assertTrue(result.summary.hasRetryableFailure)
        // The routine failed; everything downstream was skipped, untouched.
        assertEquals(SyncStatus.FAILED, statusOf(ids)[0])
        // Crucially, the workout itself is still intact on disk.
        assertEquals(BigDecimal("60.00"), database.workoutSetDao().getById(ids.setId)!!.weight)
    }

    @Test
    fun `a failed pass recovers completely on the next successful attempt`() = runTest {
        val ids = performFullWorkout()
        overrides["/routines"] = MockResponse().setResponseCode(503).setBody("{}")

        val firstResult = engine.sync()
        assertTrue(firstResult is SyncResult.Partial || firstResult is SyncResult.Failure)
        assertEquals(SyncStatus.FAILED, statusOf(ids)[0])

        // Backend comes back.
        overrides.clear()
        requestedPaths.clear()
        val secondResult = engine.sync()

        assertTrue(secondResult.toString(), secondResult is SyncResult.Success)
        assertEquals(List(5) { SyncStatus.SYNCED }, statusOf(ids))
    }

    @Test
    fun `work stranded by process death is recovered and then syncs`() = runTest {
        val ids = performFullWorkout()
        // Simulate the engine claiming every row and the process dying.
        database.routineDao().updateSyncStatus(ids.routineId, SyncStatus.SYNCING)
        database.routineExerciseDao().updateSyncStatus(ids.routineExerciseId, SyncStatus.SYNCING)
        database.workoutSessionDao().updateSyncStatus(ids.sessionId, SyncStatus.SYNCING)
        database.workoutExerciseDao().updateSyncStatus(ids.workoutExerciseId, SyncStatus.SYNCING)
        database.workoutSetDao().updateSyncStatus(ids.setId, SyncStatus.SYNCING)

        // Without recovery the pass finds nothing — the data would be lost to sync.
        assertEquals(SyncResult.NothingToSync, engine.sync())

        assertEquals(5, recovery.recoverStaleSyncing())
        val result = engine.sync()

        assertTrue(result.toString(), result is SyncResult.Success)
        assertEquals(List(5) { SyncStatus.SYNCED }, statusOf(ids))
    }

    @Test
    fun `a workout logged entirely offline syncs once connectivity returns`() = runTest {
        // The defining offline-first scenario: no network for the whole session.
        server.shutdown()
        val ids = performFullWorkout()
        engine.sync()
        assertEquals(SyncStatus.FAILED, statusOf(ids)[0])

        // A new server stands in for connectivity returning.
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty()
                requestedPaths += "${request.method} $path"
                return MockResponse().setResponseCode(201).setBody(bodyFor(path))
            }
        }
        server.start()
        engine = SyncEngineImpl(
            routineSource = routineRepository,
            routineExerciseSource = routineRepository,
            sessionSource = workoutRepository,
            workoutExerciseSource = workoutRepository,
            setSource = workoutRepository,
            deletionSource = workoutRepository,
            routineApi = server.createApi<RoutineApi>(),
            sessionApi = server.createApi<WorkoutSessionApi>(),
            logApi = server.createApi<WorkoutLogApi>(),
            ioDispatcher = UnconfinedTestDispatcher(),
        )

        val result = engine.sync()

        assertTrue(result.toString(), result is SyncResult.Success)
        assertEquals(List(5) { SyncStatus.SYNCED }, statusOf(ids))
    }

    // ---- In-progress workouts ---------------------------------------------

    @Test
    fun `an in-progress workout uploads its contents but is not sealed`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")
        val sessionId = startWorkout(routineId)
        val workoutExerciseId =
            workoutRepository.addExercise(sessionId, exerciseId, "Bench Press")
        val setId = workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("60.00"),
            repetitions = 10,
            setCategory = SetCategory.WORKING,
            rpe = null,
            rir = null,
        )
        requestedPaths.clear()

        val result = engine.sync()

        assertTrue(requestedPaths.none { it.contains("/complete") || it.contains("/discard") })
        assertTrue(result.toString(), result is SyncResult.Success)
        // Children are done; the session stays PENDING until the user finishes.
        assertEquals(
            SyncStatus.SYNCED,
            database.workoutExerciseDao().getById(workoutExerciseId)!!.syncStatus,
        )
        assertEquals(SyncStatus.SYNCED, database.workoutSetDao().getById(setId)!!.syncStatus)
        assertEquals(
            SyncStatus.PENDING,
            database.workoutSessionDao().getById(sessionId)!!.syncStatus,
        )
    }

    @Test
    fun `finishing a previously synced in-progress workout sends only the transition`() = runTest {
        val sessionId = startWorkout(null)
        val workoutExerciseId = workoutRepository.addExercise(sessionId, exerciseId, "Bench Press")
        workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("60.00"),
            repetitions = 10,
            setCategory = SetCategory.WORKING,
            rpe = null,
            rir = null,
        )
        engine.sync()

        workoutRepository.completeWorkout(sessionId)
        requestedPaths.clear()

        val result = engine.sync()

        // The session start is replayed (idempotently) and then sealed; the
        // already-SYNCED children are not re-sent.
        assertEquals(
            listOf(
                "POST /api/v1/workout-sessions",
                "PUT /api/v1/workout-sessions/$sessionId/complete",
            ),
            requestedPaths,
        )
        assertTrue(result is SyncResult.Success)
        assertEquals(
            SyncStatus.SYNCED,
            database.workoutSessionDao().getById(sessionId)!!.syncStatus,
        )
    }

    // ---- Manual entry point ------------------------------------------------

    @Test
    fun `the local writes during the walkthrough each requested a sync`() = runTest {
        trigger.reset()

        performFullWorkout()

        // createRoutine, addExercise, startWorkout, addSet, completeWorkout.
        assertEquals(5, trigger.requestCount)
    }
}

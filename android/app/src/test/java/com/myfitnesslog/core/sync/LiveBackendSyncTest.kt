package com.myfitnesslog.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.sync.engine.SyncEngineImpl
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
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Drives the real synchronization stack against a **running backend**.
 *
 * Every other sync test stands the backend in with MockWebServer, which proves
 * the client sends what we think it sends but not that a real Spring Boot
 * instance accepts it. This one closes that gap: real Room, real repositories,
 * real mappers, real Retrofit, real HTTP, real PostgreSQL.
 *
 * **This test writes real data, so it runs only against a backend that declares
 * itself disposable.** Two independent things must both be true:
 *
 * 1. `MFL_LIVE_TEST_BASE_URL` is set. There is no default — absent it, the test
 *    skips. Nothing can be written by simply having a server running.
 * 2. That backend reports `disposable: true` from `/health`, which only the
 *    `livetest` Spring profile does. If it does not, the test **fails** rather
 *    than skipping: the URL is pointing somewhere it must never write.
 *
 * The previous gate asked only whether *a* backend answered on localhost:8080,
 * and treated the answer as permission. That was true enough when no backend ran
 * locally; once M9.5 dogfooding made one permanently reachable, every ordinary
 * `testDebugUnitTest` run wrote into the system of record — 71 test routines by
 * the time it was noticed (TD-013). Reachability was never evidence of
 * disposability, so the check is now for disposability itself.
 *
 * Run it deliberately:
 *
 * ```
 * (cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=livetest)
 * MFL_LIVE_TEST_BASE_URL=http://localhost:8081/api/v1/ \
 *   ./gradlew :app:testDebugUnitTest --tests '*LiveBackendSyncTest'
 * ```
 *
 * Uses ids seeded by Flyway (`20000000-…`) so it does not depend on the
 * exercise-download path.
 */
@RunWith(RobolectricTestRunner::class)
class LiveBackendSyncTest {

    private companion object {
        /**
         * Explicitly configured target. No default: an unset variable means this
         * test does not run, which is what makes an accidental production write
         * impossible rather than merely unlikely.
         */
        val BASE_URL: String? = System.getenv("MFL_LIVE_TEST_BASE_URL")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { if (it.endsWith("/")) it else "$it/" }

        /** Flyway-seeded master data — the reference-data assumption in SYNC.md. */
        val SEEDED_EXERCISE_ID: UUID =
            UUID.fromString("20000000-0000-0000-0000-000000000024")
        val SEEDED_CATEGORY_ID: UUID =
            UUID.fromString("10000000-0000-0000-0000-000000000001")
    }

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase
    private lateinit var engine: SyncEngineImpl

    private val clock = Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC)

    /**
     * Reads `disposable` from the target's health endpoint.
     *
     * Returns null when the backend cannot be reached at all — a skip — and
     * false when it answers without declaring itself disposable, which is a
     * failure, because that is the production backend replying.
     */
    private fun backendIsDisposable(baseUrl: String): Boolean? = runCatching {
        OkHttpClient().newCall(
            Request.Builder().url("${baseUrl}health").build(),
        ).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()?.contains("\"disposable\":true") == true
        }
    }.getOrNull()

    @Before
    fun setUp() = runTest {
        val baseUrl = BASE_URL
        assumeTrue(
            "MFL_LIVE_TEST_BASE_URL is not set — skipping the live sync test. " +
                "Start a disposable backend with " +
                "`mvn spring-boot:run -Dspring-boot.run.profiles=livetest` and set " +
                "MFL_LIVE_TEST_BASE_URL=http://localhost:8081/api/v1/",
            baseUrl != null,
        )
        requireNotNull(baseUrl)

        val disposable = backendIsDisposable(baseUrl)
        assumeTrue("No backend reachable at $baseUrl — skipping live test.", disposable != null)

        // Deliberately a failure, not a skip: the target answered but is not a
        // throwaway, so this run was about to write into data someone cares
        // about. Silently skipping would hide the misconfiguration.
        assertTrue(
            "Refusing to run: $baseUrl does not report `disposable: true`. Only the " +
                "backend's `livetest` profile does. Point this at the disposable " +
                "instance (port 8081), never at the system of record — see TD-013.",
            disposable == true,
        )

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        database.exerciseCategoryDao()
            .upsert(ExerciseCategoryEntity(id = SEEDED_CATEGORY_ID, name = "Legs"))
        database.exerciseDao().upsert(
            ExerciseEntity(
                id = SEEDED_EXERCISE_ID,
                categoryId = SEEDED_CATEGORY_ID,
                name = "Barbell Back Squat",
            ),
        )

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val retrofit = Retrofit.Builder()
            .baseUrl(requireNotNull(BASE_URL))
            .client(OkHttpClient())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        val dispatcher = UnconfinedTestDispatcher()
        val trigger = RecordingSyncTrigger()
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
            routineApi = retrofit.create(RoutineApi::class.java),
            sessionApi = retrofit.create(WorkoutSessionApi::class.java),
            logApi = retrofit.create(WorkoutLogApi::class.java),
            ioDispatcher = dispatcher,
        )
    }

    @Test
    fun `a complete workout logged offline syncs to the real backend`() = runTest {
        val routineId = routineRepository.createRoutine("Live Test Routine ${UUID.randomUUID()}")
        routineRepository.addExercise(
            routineId = routineId,
            exerciseId = SEEDED_EXERCISE_ID,
            targetSets = 3,
            minTargetReps = 8,
            maxTargetReps = 12,
            targetRestSeconds = 90,
            notes = "live validation",
        )
        val sessionId = startWorkout(routineId)
        val workoutExerciseId = database.workoutExerciseDao().getBySession(sessionId).single().id
        val setId = workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("102.50"),
            repetitions = 5,
            setCategory = SetCategory.WORKING,
            rpe = BigDecimal("8.5"),
            rir = null,
        )
        workoutRepository.completeWorkout(sessionId)

        val result = engine.sync()

        assertTrue(
            "Live sync failed: ${result.summary.failures}",
            result is SyncResult.Success,
        )
        assertEquals(SyncStatus.SYNCED, database.routineDao().getById(routineId)!!.syncStatus)
        assertEquals(
            SyncStatus.SYNCED,
            database.workoutSessionDao().getById(sessionId)!!.syncStatus,
        )
        assertEquals(SyncStatus.SYNCED, database.workoutSetDao().getById(setId)!!.syncStatus)

        // Print the ids so the run can be reconciled against PostgreSQL directly.
        println("LIVE_SYNC routine=$routineId session=$sessionId set=$setId")
    }

    @Test
    fun `removing an exercise from a routine reaches the real backend`() = runTest {
        // The regression test for M11 Phase 1 defect D-1, verified against a real
        // backend rather than a fake: the deletion was previously filtered out of
        // the pending query and never uploaded, leaving the backend permanently
        // ahead of the phone.
        val routineId = routineRepository.createRoutine("Live Delete ${UUID.randomUUID()}")
        val exerciseId = routineRepository.addExercise(
            routineId = routineId,
            exerciseId = SEEDED_EXERCISE_ID,
            targetSets = 3,
            minTargetReps = 8,
            maxTargetReps = 12,
            targetRestSeconds = 90,
            notes = null,
        )
        assertTrue(engine.sync() is SyncResult.Success)

        routineRepository.removeExercise(exerciseId)
        val result = engine.sync()

        // The API exposes no read endpoint for routine exercises, so the
        // backend-side effect is confirmed directly in PostgreSQL by the phase
        // verification; what this asserts is that the client actually issued the
        // deletion and accepted the response, which is precisely what D-1 broke.
        assertTrue("Deletion sync failed: ${result.summary.failures}", result is SyncResult.Success)
        assertEquals(
            SyncStatus.SYNCED,
            database.routineExerciseDao().getById(exerciseId)!!.syncStatus,
        )
        println("LIVE_DELETE routineExercise=$exerciseId removed from backend")
    }

    @Test
    fun `deleting a routine reaches the real backend`() = runTest {
        val routineId = routineRepository.createRoutine("Live Routine Delete ${UUID.randomUUID()}")
        assertTrue(engine.sync() is SyncResult.Success)

        routineRepository.deleteRoutine(routineId)
        val result = engine.sync()

        assertTrue("Routine delete failed: ${result.summary.failures}", result is SyncResult.Success)

        // The backend soft-deletes, so the routine disappears from the list.
        val listBody = OkHttpClient().newCall(
            Request.Builder().url("${requireNotNull(BASE_URL)}routines").build(),
        ).execute().use { it.body!!.string() }
        assertTrue(
            "Deleted routine still present in GET /routines",
            !listBody.contains(routineId.toString()),
        )
        println("LIVE_DELETE routine=$routineId soft-deleted on backend")
    }

    @Test
    fun `replaying the same pass against the real backend is idempotent`() = runTest {
        val routineId = routineRepository.createRoutine("Live Replay ${UUID.randomUUID()}")
        val sessionId = startWorkout(routineId)
        workoutRepository.completeWorkout(sessionId)

        assertTrue(engine.sync() is SyncResult.Success)

        // Force the whole graph back to PENDING and upload it again — the second
        // pass must update in place (200) rather than duplicating (201/409).
        database.routineDao().updateSyncStatus(routineId, SyncStatus.PENDING)
        database.workoutSessionDao().updateSyncStatus(sessionId, SyncStatus.PENDING)

        val replay = engine.sync()

        assertTrue("Replay failed: ${replay.summary.failures}", replay is SyncResult.Success)
        println("LIVE_REPLAY routine=$routineId session=$sessionId")
    }

    @Test
    fun `a set deleted after it was uploaded is removed from the real backend`() = runTest {
        // The failure ADR-0007 exists to prevent: the set survives on the
        // backend and the web client shows a set the phone does not.
        val routineId = routineRepository.createRoutine("Live Delete ${UUID.randomUUID()}")
        val sessionId = startWorkout(routineId)
        val workoutExerciseId =
            workoutRepository.addExercise(sessionId, SEEDED_EXERCISE_ID, "Barbell Back Squat")
        val keptSetId = workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("100.00"),
            repetitions = 5,
            setCategory = SetCategory.WORKING,
            rpe = null,
            rir = null,
        )
        val doomedSetId = workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("60.00"),
            repetitions = 12,
            setCategory = SetCategory.WORKING,
            rpe = null,
            rir = null,
        )

        // Upload both sets first, so the deletion has something real to remove.
        assertTrue(engine.sync() is SyncResult.Success)

        workoutRepository.deleteSet(doomedSetId)
        workoutRepository.completeWorkout(sessionId)
        val result = engine.sync()

        assertTrue("Deletion sync failed: ${result.summary.failures}", result is SyncResult.Success)
        assertTrue(
            "The tombstone should be cleared once the backend accepted the delete.",
            workoutRepository.getPendingWorkoutSetDeletions().isEmpty(),
        )

        // Read the backend's own snapshot back: it is the assertion that matters.
        val detail = OkHttpClient().newCall(
            Request.Builder().url("${requireNotNull(BASE_URL)}workout-sessions/$sessionId").build(),
        ).execute().use { it.body!!.string() }
        assertTrue("Deleted set still present on the backend: $detail", !detail.contains("$doomedSetId"))
        assertTrue("Kept set missing from the backend: $detail", detail.contains("$keptSetId"))

        println("LIVE_DELETE session=$sessionId kept=$keptSetId deleted=$doomedSetId")
    }
}

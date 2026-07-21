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
 * **Skipped unless a backend is reachable on localhost:8080.** It is not part of
 * the normal suite — CI and everyday runs have no backend, and
 * [assumeTrue] makes it a skip rather than a failure. Run it deliberately:
 *
 * ```
 * (cd backend && mvn spring-boot:run)
 * ./gradlew :app:testDebugUnitTest --tests '*LiveBackendSyncTest'
 * ```
 *
 * Uses ids seeded by Flyway (`20000000-…`) so it does not depend on the
 * exercise-download path.
 */
@RunWith(RobolectricTestRunner::class)
class LiveBackendSyncTest {

    private companion object {
        const val BASE_URL = "http://localhost:8080/api/v1/"

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

    private fun backendIsUp(): Boolean = runCatching {
        OkHttpClient().newCall(
            Request.Builder().url("http://localhost:8080/api/v1/health").build(),
        ).execute().use { it.isSuccessful }
    }.getOrDefault(false)

    @Before
    fun setUp() = runTest {
        assumeTrue("No backend on localhost:8080 — skipping live test.", backendIsUp())

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
            .baseUrl(BASE_URL)
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
}

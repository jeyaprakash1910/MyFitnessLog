package com.myfitnesslog.core.sync.restore

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.ExerciseCategoryRepository
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import com.myfitnesslog.feature.routine.data.remote.RoutineDetailResponseDto
import com.myfitnesslog.feature.routine.data.remote.RoutineResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutExerciseDetailResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionDetailResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutSetResponseDto
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Restore at the shape of real production data, rather than the one-of-everything
 * shape the other tests use.
 *
 * Written while chasing an apparent partial restore on a physical device, which
 * turned out not to be a code fault at all (Android Auto Backup was reinstating a
 * stale database, so restore correctly declined to run). The test is kept anyway,
 * because the gap it was written to close is real: every other restore test uses
 * one session containing one exercise, and at that size "writes everything" and
 * "writes the first few then stops" are indistinguishable. Twenty exercises across
 * two sessions can tell those apart.
 *
 * It also mirrors a detail of the real data that the smaller fixtures miss: most
 * exercises in a session carry no sets at all.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestoreScaleTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var restoreManager: RestoreManagerImpl

    private val categoryId = UUID.randomUUID()
    private val exerciseIds = List(10) { UUID.randomUUID() }
    private val sessionIds = List(2) { UUID.randomUUID() }

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()

        restoreManager = RestoreManagerImpl(
            routineApi = EmptyRoutineApi(),
            workoutSessionApi = TwoSessionsOfTenApi(),
            exerciseCategoryRepository = SeedingCategoryRepository(),
            exerciseRepository = SeedingExerciseRepository(),
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            workoutSetDao = database.workoutSetDao(),
            clock = Clock.fixed(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() = database.close()

    private inner class SeedingCategoryRepository : ExerciseCategoryRepository {
        override fun observeAll(): Flow<List<ExerciseCategoryEntity>> = flowOf(emptyList())
        override fun observeById(id: UUID): Flow<ExerciseCategoryEntity?> = flowOf(null)
        override suspend fun fetch(): List<ExerciseCategoryEntity> = emptyList()
        override suspend fun refresh() {
            database.exerciseCategoryDao().upsertAll(
                listOf(ExerciseCategoryEntity(id = categoryId, name = "Chest", displayOrder = 1)),
            )
        }
    }

    private inner class SeedingExerciseRepository : ExerciseRepository {
        override fun observeAll(): Flow<List<ExerciseEntity>> = flowOf(emptyList())
        override fun observeById(id: UUID): Flow<ExerciseEntity?> = flowOf(null)
        override fun observeFiltered(categoryId: UUID?, query: String?): Flow<List<ExerciseEntity>> =
            flowOf(emptyList())

        override suspend fun refreshLibrary() {
            database.exerciseDao().upsertAll(
                exerciseIds.mapIndexed { i, id ->
                    ExerciseEntity(id = id, categoryId = categoryId, name = "Exercise $i")
                },
            )
        }
    }

    private inner class EmptyRoutineApi : RoutineApi by ThrowingRoutineApi() {
        override suspend fun getRoutines(): List<RoutineResponseDto> = emptyList()
        override suspend fun getRoutine(id: String): RoutineDetailResponseDto = error("unused")
    }

    /** Mirrors production: two completed sessions, ten exercises each. */
    private inner class TwoSessionsOfTenApi : WorkoutSessionApi by ThrowingSessionApi() {
        override suspend fun getWorkoutSessions() = sessionIds.map {
            WorkoutSessionResponseDto(
                id = it.toString(),
                status = "COMPLETED",
                startedAt = Instant.parse("2026-08-01T09:00:00Z"),
            )
        }

        override suspend fun getWorkoutSession(id: String) = WorkoutSessionDetailResponseDto(
            id = id,
            status = "COMPLETED",
            startedAt = Instant.parse("2026-08-01T09:00:00Z"),
            endedAt = Instant.parse("2026-08-01T10:00:00Z"),
            exercises = (0 until 10).map { order ->
                WorkoutExerciseDetailResponseDto(
                    id = UUID.randomUUID().toString(),
                    exerciseId = exerciseIds[order].toString(),
                    exerciseName = "Exercise $order",
                    exerciseOrder = order,
                    targetSets = 3,
                    minTargetReps = 8,
                    maxTargetReps = 12,
                    targetRestSeconds = 90,
                    // Only the first exercise has sets, exactly as the real data does.
                    sets = if (order == 0) {
                        (1..3).map { n ->
                            WorkoutSetResponseDto(
                                id = UUID.randomUUID().toString(),
                                workoutExerciseId = UUID.randomUUID().toString(),
                                setNumber = n,
                                weight = BigDecimal("70.00"),
                                repetitions = 5,
                                setCategory = "WORKING",
                                isCompleted = true,
                            )
                        }
                    } else {
                        emptyList()
                    },
                )
            },
        )
    }

    @Test
    fun `both sessions and all twenty exercises are restored`() = runTest {
        val outcome = restoreManager.restoreIfEmpty()

        assertEquals(RestoreOutcome.Restored(routines = 0, sessions = 2), outcome)
        assertEquals("sessions", 2, database.workoutSessionDao().count())
        assertEquals(
            "exercises for session 1",
            10,
            database.workoutExerciseDao().getBySession(sessionIds[0]).size,
        )
        assertEquals(
            "exercises for session 2",
            10,
            database.workoutExerciseDao().getBySession(sessionIds[1]).size,
        )
    }
}

package com.myfitnesslog.core.sync.restore

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.exercise.data.ExerciseCategoryRepository
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
import com.myfitnesslog.feature.routine.data.remote.AddRoutineExerciseRequestDto
import com.myfitnesslog.feature.routine.data.remote.CreateRoutineRequestDto
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import com.myfitnesslog.feature.routine.data.remote.UpdateRoutineExerciseRequestDto
import com.myfitnesslog.feature.routine.data.remote.UpdateRoutineRequestDto
import com.myfitnesslog.feature.routine.data.remote.RoutineDetailResponseDto
import com.myfitnesslog.feature.routine.data.remote.RoutineExerciseResponseDto
import com.myfitnesslog.feature.routine.data.remote.RoutineResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutExerciseDetailResponseDto
import com.myfitnesslog.feature.workout.data.remote.CompleteWorkoutSessionRequestDto
import com.myfitnesslog.feature.workout.data.remote.DiscardWorkoutSessionRequestDto
import com.myfitnesslog.feature.workout.data.remote.StartWorkoutSessionRequestDto
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests for the restore path (ADR-0017, Stage 1) over a real in-memory Room
 * database with fake APIs.
 *
 * Two properties carry most of the weight here, and neither is obvious from
 * reading the implementation:
 *
 * - **Restore must not run when the device already has data.** Getting this wrong
 *   would overwrite a real training log with a stale server copy, which is worse
 *   than the problem restore exists to solve.
 * - **Restored rows must be SYNCED, not PENDING.** The entity default is PENDING,
 *   so simply forgetting to set it would make the app immediately re-upload
 *   everything it just downloaded.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RestoreManagerImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineApi: FakeRoutineApi
    private lateinit var sessionApi: FakeWorkoutSessionApi
    private lateinit var restoreManager: RestoreManagerImpl

    private val categoryId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()
    private val routineId = UUID.randomUUID()
    private val sessionId = UUID.randomUUID()
    private val restoredAt = Instant.parse("2026-08-07T10:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        routineApi = FakeRoutineApi()
        sessionApi = FakeWorkoutSessionApi()

        restoreManager = RestoreManagerImpl(
            routineApi = routineApi,
            workoutSessionApi = sessionApi,
            // The exercise library is a precondition, not the subject: routine and
            // workout rows have a RESTRICT foreign key to exercise, so the fakes
            // seed it exactly as the real repositories would.
            exerciseCategoryRepository = SeedingCategoryRepository(),
            exerciseRepository = SeedingExerciseRepository(),
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            workoutSetDao = database.workoutSetDao(),
            clock = Clock.fixed(restoredAt, ZoneOffset.UTC),
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() = database.close()

    // --- fakes ---------------------------------------------------------------

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
                listOf(ExerciseEntity(id = exerciseId, categoryId = categoryId, name = "Bench Press")),
            )
        }
    }

    private inner class FakeRoutineApi : RoutineApi by ThrowingRoutineApi() {
        var failWith: Exception? = null
        var routines = listOf(RoutineResponseDto(id = routineId.toString(), name = "Push A"))
        var detail = RoutineDetailResponseDto(
            id = routineId.toString(),
            name = "Push A",
            exercises = listOf(
                RoutineExerciseResponseDto(
                    id = UUID.randomUUID().toString(),
                    exerciseId = exerciseId.toString(),
                    exerciseOrder = 0,
                    targetSets = 3,
                    minTargetReps = 8,
                    maxTargetReps = 12,
                    targetRestSeconds = 90,
                    notes = "warm up first",
                ),
            ),
        )

        override suspend fun getRoutines(): List<RoutineResponseDto> {
            failWith?.let { throw it }
            return routines
        }

        override suspend fun getRoutine(id: String): RoutineDetailResponseDto = detail
    }

    private inner class FakeWorkoutSessionApi : WorkoutSessionApi by ThrowingSessionApi() {
        var sessions = listOf(
            WorkoutSessionResponseDto(
                id = sessionId.toString(),
                status = "COMPLETED",
                startedAt = Instant.parse("2026-08-01T09:00:00Z"),
                endedAt = Instant.parse("2026-08-01T10:00:00Z"),
            ),
        )
        var detail = WorkoutSessionDetailResponseDto(
            id = sessionId.toString(),
            routineId = routineId.toString(),
            status = "COMPLETED",
            startedAt = Instant.parse("2026-08-01T09:00:00Z"),
            endedAt = Instant.parse("2026-08-01T10:00:00Z"),
            notes = "felt strong",
            exercises = listOf(
                WorkoutExerciseDetailResponseDto(
                    id = UUID.randomUUID().toString(),
                    exerciseId = exerciseId.toString(),
                    exerciseName = "Bench Press",
                    exerciseOrder = 0,
                    targetSets = 3,
                    minTargetReps = 8,
                    maxTargetReps = 12,
                    sets = listOf(
                        WorkoutSetResponseDto(
                            id = UUID.randomUUID().toString(),
                            workoutExerciseId = UUID.randomUUID().toString(),
                            setNumber = 1,
                            weight = BigDecimal("60.50"),
                            repetitions = 10,
                            setCategory = "WORKING",
                            isCompleted = true,
                        ),
                    ),
                ),
            ),
        )

        override suspend fun getWorkoutSessions(): List<WorkoutSessionResponseDto> = sessions
        override suspend fun getWorkoutSession(id: String): WorkoutSessionDetailResponseDto = detail
    }

    // --- tests ---------------------------------------------------------------

    @Test
    fun `an empty database is rebuilt from the backend`() = runTest {
        val outcome = restoreManager.restoreIfEmpty()

        assertEquals(RestoreOutcome.Restored(routines = 1, sessions = 1), outcome)

        val routine = database.routineDao().getById(routineId)
        assertNotNull("routine was not restored", routine)
        assertEquals("Push A", routine!!.name)
        assertEquals(1, database.routineExerciseDao().getByRoutine(routineId).size)

        val session = database.workoutSessionDao().getById(sessionId)
        assertNotNull("session was not restored", session)
        assertEquals(WorkoutStatus.COMPLETED, session!!.status)
        assertEquals("felt strong", session.notes)
    }

    @Test
    fun `restored rows are marked synced so they are not immediately re-uploaded`() = runTest {
        restoreManager.restoreIfEmpty()

        // The entity default is PENDING. If the mappers forgot to override it,
        // restore would hand the sync engine the entire downloaded history as
        // work to push straight back up.
        assertEquals(SyncStatus.SYNCED, database.routineDao().getById(routineId)!!.syncStatus)
        assertEquals(
            SyncStatus.SYNCED,
            database.routineExerciseDao().getByRoutine(routineId).first().syncStatus,
        )
        assertEquals(SyncStatus.SYNCED, database.workoutSessionDao().getById(sessionId)!!.syncStatus)
        assertTrue(database.routineDao().getPendingSync().isEmpty())
    }

    @Test
    fun `set values survive the round trip with their decimal scale`() = runTest {
        restoreManager.restoreIfEmpty()

        val exercises = database.workoutExerciseDao().getBySession(sessionId)
        assertEquals(1, exercises.size)
        val sets = database.workoutSetDao().getByExercise(exercises.first().id)
        assertEquals(1, sets.size)
        // 60.50, not 60.5: weight is NUMERIC on the backend and the scale is part
        // of what the user entered.
        assertEquals(0, BigDecimal("60.50").compareTo(sets.first().weight))
        assertEquals("60.50", sets.first().weight.toPlainString())
        assertEquals(10, sets.first().repetitions)
    }

    @Test
    fun `a database with existing data is left completely alone`() = runTest {
        // The dangerous case. A device with a real training log must never have it
        // replaced by whatever the server happens to hold.
        val localRoutine = RoutineEntity(
            id = UUID.randomUUID(),
            name = "My own routine",
            createdAt = restoredAt,
            updatedAt = restoredAt,
        )
        database.routineDao().upsert(localRoutine)

        val outcome = restoreManager.restoreIfEmpty()

        assertEquals(RestoreOutcome.NotNeeded, outcome)
        assertEquals(1, database.routineDao().count())
        assertNull("backend routine must not have been written", database.routineDao().getById(routineId))
        assertEquals(0, database.workoutSessionDao().count())
    }

    @Test
    fun `a device holding only history is also left alone`() = runTest {
        // Emptiness means both tables. A device with workouts but no routines has
        // still recorded real training.
        restoreManager.restoreIfEmpty()
        database.routineDao().let { dao -> dao.getById(routineId)?.let { dao.upsert(it) } }
        val sessionsAfterFirst = database.workoutSessionDao().count()

        val second = restoreManager.restoreIfEmpty()

        assertEquals(RestoreOutcome.NotNeeded, second)
        assertEquals(sessionsAfterFirst, database.workoutSessionDao().count())
    }

    @Test
    fun `an unreachable backend fails quietly and leaves the database empty`() = runTest {
        // The ordinary case on a fresh install: no signal, or the instance is
        // asleep. It must not crash a launch.
        routineApi.failWith = IOException("no network")

        val outcome = restoreManager.restoreIfEmpty()

        assertTrue("expected Failed but was $outcome", outcome is RestoreOutcome.Failed)
        assertEquals(0, database.routineDao().count())
        assertEquals(0, database.workoutSessionDao().count())
    }

    @Test
    fun `a malformed response fails quietly rather than crashing`() = runTest {
        routineApi.failWith = IllegalArgumentException("Invalid UUID string")

        val outcome = restoreManager.restoreIfEmpty()

        assertTrue("expected Failed but was $outcome", outcome is RestoreOutcome.Failed)
        assertEquals(0, database.routineDao().count())
    }

    @Test
    fun `a backend with nothing in it reports nothing to restore`() = runTest {
        routineApi.routines = emptyList()
        sessionApi.sessions = emptyList()

        assertEquals(RestoreOutcome.NothingToRestore, restoreManager.restoreIfEmpty())
    }

    @Test
    fun `a failed restore can be retried on the next launch`() = runTest {
        routineApi.failWith = IOException("no network")
        assertTrue(restoreManager.restoreIfEmpty() is RestoreOutcome.Failed)

        // Nothing was written, so the database is still empty and the next attempt
        // is still eligible. A one-shot flag here would strand the user forever
        // after a single offline launch.
        routineApi.failWith = null
        assertEquals(
            RestoreOutcome.Restored(routines = 1, sessions = 1),
            restoreManager.restoreIfEmpty(),
        )
    }
}

/**
 * Fail-fast stand-ins for the write half of each API.
 *
 * Restore only reads, so any call to a write method means the code under test did
 * something it should not. Throwing makes that a loud test failure rather than a
 * silent no-op that a reviewer has to notice.
 */
private class ThrowingRoutineApi : RoutineApi {
    private fun no(): Nothing = throw UnsupportedOperationException("restore must not write")
    override suspend fun getRoutines(): List<RoutineResponseDto> = no()
    override suspend fun getRoutine(id: String): RoutineDetailResponseDto = no()
    override suspend fun createRoutine(request: CreateRoutineRequestDto) = no()
    override suspend fun updateRoutine(id: String, request: UpdateRoutineRequestDto) = no()
    override suspend fun deleteRoutine(id: String) = no()
    override suspend fun addRoutineExercise(routineId: String, request: AddRoutineExerciseRequestDto) = no()
    override suspend fun updateRoutineExercise(id: String, request: UpdateRoutineExerciseRequestDto) = no()
    override suspend fun deleteRoutineExercise(id: String) = no()
}

private class ThrowingSessionApi : WorkoutSessionApi {
    private fun no(): Nothing = throw UnsupportedOperationException("restore must not write")
    override suspend fun getWorkoutSessions(): List<WorkoutSessionResponseDto> = no()
    override suspend fun getWorkoutSession(id: String): WorkoutSessionDetailResponseDto = no()
    override suspend fun startWorkoutSession(request: StartWorkoutSessionRequestDto) = no()
    override suspend fun completeWorkoutSession(id: String, request: CompleteWorkoutSessionRequestDto) = no()
    override suspend fun discardWorkoutSession(id: String, request: DiscardWorkoutSessionRequestDto) = no()
}

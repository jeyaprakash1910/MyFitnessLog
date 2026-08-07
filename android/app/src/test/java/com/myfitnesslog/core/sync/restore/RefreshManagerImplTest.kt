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
import com.myfitnesslog.feature.routine.data.remote.RoutineApi
import com.myfitnesslog.feature.routine.data.remote.RoutineDetailResponseDto
import com.myfitnesslog.feature.routine.data.remote.RoutineResponseDto
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseTombstoneEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionApi
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionDetailResponseDto
import com.myfitnesslog.feature.workout.data.remote.WorkoutSessionResponseDto
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
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Tests for periodic refresh (ADR-0017 Stage 2).
 *
 * Restore could be careless about conflicts because it only ever ran against an
 * empty database. Refresh cannot: it runs against real data, so every test here is
 * ultimately asking the same question, which is whether a pull can destroy
 * something the user did. The dangerous cases get the most attention:
 *
 * - a locally-edited row must survive contact with an older server copy
 * - an in-progress workout must survive, even though the history endpoint has
 *   never heard of it
 * - a pending deletion must not be undone by re-downloading the row it removed
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class RefreshManagerImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineApi: FakeRoutineApi
    private lateinit var sessionApi: FakeSessionApi
    private lateinit var refreshManager: RefreshManagerImpl

    private val categoryId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()
    private val remoteRoutineId = UUID.randomUUID()
    private val remoteSessionId = UUID.randomUUID()
    private val now = Instant.parse("2026-08-07T12:00:00Z")

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        routineApi = FakeRoutineApi()
        sessionApi = FakeSessionApi()

        refreshManager = RefreshManagerImpl(
            routineApi = routineApi,
            workoutSessionApi = sessionApi,
            exerciseCategoryRepository = SeedingCategoryRepository(),
            exerciseRepository = SeedingExerciseRepository(),
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            workoutSetDao = database.workoutSetDao(),
            clock = Clock.fixed(now, ZoneOffset.UTC),
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
                listOf(ExerciseEntity(id = exerciseId, categoryId = categoryId, name = "Bench")),
            )
        }
    }

    private inner class FakeRoutineApi : RoutineApi by ThrowingRoutineApi() {
        var failWith: Exception? = null
        var routines = listOf(RoutineResponseDto(id = remoteRoutineId.toString(), name = "Server Push"))

        override suspend fun getRoutines(): List<RoutineResponseDto> {
            failWith?.let { throw it }
            return routines
        }

        override suspend fun getRoutine(id: String) =
            RoutineDetailResponseDto(id = id, name = "Server Push", exercises = emptyList())
    }

    private inner class FakeSessionApi : WorkoutSessionApi by ThrowingSessionApi() {
        var sessions = listOf(
            WorkoutSessionResponseDto(
                id = remoteSessionId.toString(),
                status = "COMPLETED",
                startedAt = Instant.parse("2026-08-01T09:00:00Z"),
            ),
        )

        override suspend fun getWorkoutSessions() = sessions
        override suspend fun getWorkoutSession(id: String) = WorkoutSessionDetailResponseDto(
            id = id,
            status = "COMPLETED",
            startedAt = Instant.parse("2026-08-01T09:00:00Z"),
            endedAt = Instant.parse("2026-08-01T10:00:00Z"),
            exercises = emptyList(),
        )
    }

    // --- helpers -------------------------------------------------------------

    private suspend fun localRoutine(
        id: UUID = UUID.randomUUID(),
        name: String = "Local",
        status: SyncStatus = SyncStatus.SYNCED,
    ): UUID {
        database.routineDao().upsert(
            RoutineEntity(id = id, name = name, createdAt = now, updatedAt = now, syncStatus = status),
        )
        return id
    }

    private suspend fun localSession(
        id: UUID = UUID.randomUUID(),
        status: WorkoutStatus,
        syncStatus: SyncStatus = SyncStatus.SYNCED,
    ): UUID {
        database.workoutSessionDao().upsert(
            WorkoutSessionEntity(
                id = id,
                routineId = null,
                status = status,
                startedAt = now,
                createdAt = now,
                updatedAt = now,
                syncStatus = syncStatus,
            ),
        )
        return id
    }

    // --- tests ---------------------------------------------------------------

    @Test
    fun `the backend's rows are applied locally`() = runTest {
        val outcome = refreshManager.refresh()

        assertTrue("expected Applied but was $outcome", outcome is RefreshOutcome.Applied)
        assertEquals("Server Push", database.routineDao().getById(remoteRoutineId)?.name)
        assertNotNull(database.workoutSessionDao().getById(remoteSessionId))
    }

    @Test
    fun `refresh defers entirely while anything is still waiting to upload`() = runTest {
        localRoutine(status = SyncStatus.PENDING)

        val outcome = refreshManager.refresh()

        // Not merely "skip that row": the whole pull is abandoned. While the
        // outbox has work the backend is known to be behind, so its answer is not
        // worth having yet.
        assertEquals(RefreshOutcome.Deferred, outcome)
        assertNull(database.routineDao().getById(remoteRoutineId))
    }

    @Test
    fun `an unreplayed deletion defers the refresh instead of resurrecting the row`() = runTest {
        // Without this, refresh would download the exercise the user deleted and
        // put it straight back. TD-014 made the deletion reach the backend; this
        // makes sure refresh waits for it to get there.
        database.workoutExerciseDao().upsertTombstone(
            WorkoutExerciseTombstoneEntity(
                workoutExerciseId = UUID.randomUUID(),
                workoutSessionId = UUID.randomUUID(),
                deletedAt = now,
            ),
        )

        assertEquals(RefreshOutcome.Deferred, refreshManager.refresh())
    }

    @Test
    fun `a row mid-upload is skipped rather than overwritten`() = runTest {
        // This is what the per-row check exists for, and it is not redundant with
        // the outbox check above. The pending queries match PENDING and FAILED
        // only, so a row currently being uploaded (SYNCING) is invisible to them:
        // the pass starts, and this row still must not be replaced by the server's
        // older copy while its own upload is in flight.
        database.routineDao().upsert(
            RoutineEntity(
                id = remoteRoutineId,
                name = "My edit",
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.SYNCING,
            ),
        )

        val outcome = refreshManager.refresh()

        assertEquals("My edit", database.routineDao().getById(remoteRoutineId)?.name)
        assertEquals(1, (outcome as RefreshOutcome.Applied).skippedPendingLocal)
    }

    @Test
    fun `a failed upload defers the refresh rather than skipping one row`() = runTest {
        // FAILED counts as pending, so the whole pull waits. That is the right
        // call: a failed upload means the backend is missing something, and
        // reconciling against a backend known to be incomplete could delete rows
        // that only failed to arrive.
        localRoutine(name = "Failed to upload", status = SyncStatus.FAILED)

        assertEquals(RefreshOutcome.Deferred, refreshManager.refresh())
    }

    @Test
    fun `a routine deleted on another device is removed locally`() = runTest {
        val stale = localRoutine(name = "Deleted elsewhere")

        val outcome = refreshManager.refresh()

        assertNull("a routine the backend no longer lists must go", database.routineDao().getById(stale))
        assertEquals(1, (outcome as RefreshOutcome.Applied).routinesRemoved)
    }

    @Test
    fun `an in-progress workout survives even though history never lists it`() = runTest {
        // The history endpoint returns completed sessions only, so an active
        // workout is absent by design. Reading that absence as a deletion would
        // wipe the session the user is performing right now.
        val active = localSession(status = WorkoutStatus.IN_PROGRESS)

        refreshManager.refresh()

        assertNotNull("the active workout was deleted", database.workoutSessionDao().getById(active))
    }

    @Test
    fun `a discarded workout also survives, being absent from history by design`() = runTest {
        val discarded = localSession(status = WorkoutStatus.DISCARDED)

        refreshManager.refresh()

        assertNotNull(database.workoutSessionDao().getById(discarded))
    }

    @Test
    fun `a completed workout the backend no longer has is removed`() = runTest {
        val stale = localSession(status = WorkoutStatus.COMPLETED)

        val outcome = refreshManager.refresh()

        assertNull(database.workoutSessionDao().getById(stale))
        assertEquals(1, (outcome as RefreshOutcome.Applied).sessionsRemoved)
    }

    @Test
    fun `an unreachable backend leaves local data exactly as it was`() = runTest {
        val mine = localRoutine(name = "Mine")
        routineApi.failWith = IOException("offline")

        val outcome = refreshManager.refresh()

        assertTrue(outcome is RefreshOutcome.Failed)
        assertEquals("Mine", database.routineDao().getById(mine)?.name)
    }
}

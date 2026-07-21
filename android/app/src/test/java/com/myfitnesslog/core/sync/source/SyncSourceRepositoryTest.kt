package com.myfitnesslog.core.sync.source

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.core.sync.testing.SyncEntityFixtures
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests the repository-backed `SyncSource` implementations against a real Room
 * database.
 *
 * The critical property under test is that recording a sync writes **only**
 * syncStatus. If it also stamped updatedAt, every successful upload would
 * re-dirty its own row and the engine would loop forever — so these assertions
 * guard against a genuinely subtle production bug.
 */
@RunWith(RobolectricTestRunner::class)
class SyncSourceRepositoryTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var workoutRepository: WorkoutRepositoryImpl

    private val categoryId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()

    /** A clock deliberately far ahead, so any stray timestamp write is obvious. */
    private val laterClock = Clock.fixed(Instant.parse("2030-01-01T00:00:00Z"), ZoneOffset.UTC)

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

        routineRepository = RoutineRepositoryImpl(
            syncTrigger = RecordingSyncTrigger(),
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = laterClock,
        )
        workoutRepository = WorkoutRepositoryImpl(
            syncTrigger = RecordingSyncTrigger(),
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = laterClock,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---- Pending discovery ------------------------------------------------

    @Test
    fun `pending routines include PENDING and FAILED but exclude SYNCED and SYNCING`() = runTest {
        val pending = UUID.randomUUID()
        val failed = UUID.randomUUID()
        val synced = UUID.randomUUID()
        val syncing = UUID.randomUUID()
        listOf(
            SyncEntityFixtures.routine(id = pending, syncStatus = SyncStatus.PENDING),
            SyncEntityFixtures.routine(id = failed, syncStatus = SyncStatus.FAILED),
            SyncEntityFixtures.routine(id = synced, syncStatus = SyncStatus.SYNCED),
            SyncEntityFixtures.routine(id = syncing, syncStatus = SyncStatus.SYNCING),
        ).forEach { database.routineDao().upsert(it) }

        val result = routineRepository.getPendingRoutines().map { it.id }

        assertEquals(setOf(pending, failed), result.toSet())
    }

    @Test
    fun `soft-deleted routines are excluded from pending work`() = runTest {
        // Deletion propagation is deferred; uploading a deleted routine as a
        // create would resurrect it on the backend.
        database.routineDao().upsert(SyncEntityFixtures.routine(isDeleted = true))

        assertTrue(routineRepository.getPendingRoutines().isEmpty())
    }

    @Test
    fun `pending routines are ordered oldest first`() = runTest {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        database.routineDao().upsert(
            SyncEntityFixtures.routine(id = second)
                .copy(createdAt = Instant.parse("2026-07-21T12:00:00Z")),
        )
        database.routineDao().upsert(
            SyncEntityFixtures.routine(id = first)
                .copy(createdAt = Instant.parse("2026-07-21T08:00:00Z")),
        )

        assertEquals(
            listOf(first, second),
            routineRepository.getPendingRoutines().map { it.id },
        )
    }

    @Test
    fun `pending sets carry their grandparent session id`() = runTest {
        val sessionId = UUID.randomUUID()
        val workoutExerciseId = UUID.randomUUID()
        database.workoutSessionDao().upsert(
            SyncEntityFixtures.session(id = sessionId, routineId = null),
        )
        database.workoutExerciseDao().upsert(
            SyncEntityFixtures.workoutExercise(id = workoutExerciseId, sessionId = sessionId)
                .copy(exerciseId = exerciseId),
        )
        database.workoutSetDao().upsert(
            SyncEntityFixtures.pendingSet(
                workoutExerciseId = workoutExerciseId,
                sessionId = sessionId,
            ).set,
        )

        val pending = workoutRepository.getPendingWorkoutSets().single()

        assertEquals(sessionId, pending.workoutSessionId)
        assertEquals(workoutExerciseId, pending.set.workoutExerciseId)
    }

    @Test
    fun `pending workout exercises are ordered by session then position`() = runTest {
        val sessionId = UUID.randomUUID()
        val second = UUID.randomUUID()
        val first = UUID.randomUUID()
        database.workoutSessionDao().upsert(
            SyncEntityFixtures.session(id = sessionId, routineId = null),
        )
        database.workoutExerciseDao().upsert(
            SyncEntityFixtures.workoutExercise(id = second, sessionId = sessionId, exerciseOrder = 1)
                .copy(exerciseId = exerciseId),
        )
        database.workoutExerciseDao().upsert(
            SyncEntityFixtures.workoutExercise(id = first, sessionId = sessionId, exerciseOrder = 0)
                .copy(exerciseId = exerciseId),
        )

        assertEquals(
            listOf(first, second),
            workoutRepository.getPendingWorkoutExercises().map { it.id },
        )
    }

    // ---- Status-only updates ----------------------------------------------

    @Test
    fun `recording a routine sync changes only syncStatus`() = runTest {
        val original = SyncEntityFixtures.routine()
        database.routineDao().upsert(original)

        routineRepository.setRoutineSyncStatus(original.id, SyncStatus.SYNCED)

        val stored = database.routineDao().getById(original.id)!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        // Everything else must be byte-identical — especially updatedAt, which
        // the repository's normal write path would have advanced to 2030.
        assertEquals(original, stored.copy(syncStatus = original.syncStatus))
        assertEquals(original.updatedAt, stored.updatedAt)
        assertEquals(original.createdAt, stored.createdAt)
    }

    @Test
    fun `a synced routine does not reappear as pending work`() = runTest {
        // The regression this guards: if the status write also bumped updatedAt,
        // nothing would break here — but combined with a dirty-check it would
        // loop. Asserting the row leaves the queue is the observable half.
        val routine = SyncEntityFixtures.routine()
        database.routineDao().upsert(routine)

        routineRepository.setRoutineSyncStatus(routine.id, SyncStatus.SYNCED)

        assertTrue(routineRepository.getPendingRoutines().isEmpty())
    }

    @Test
    fun `recording a workout set sync changes only syncStatus`() = runTest {
        val sessionId = UUID.randomUUID()
        val workoutExerciseId = UUID.randomUUID()
        database.workoutSessionDao().upsert(
            SyncEntityFixtures.session(id = sessionId, routineId = null),
        )
        database.workoutExerciseDao().upsert(
            SyncEntityFixtures.workoutExercise(id = workoutExerciseId, sessionId = sessionId)
                .copy(exerciseId = exerciseId),
        )
        val original = SyncEntityFixtures.pendingSet(
            workoutExerciseId = workoutExerciseId,
            sessionId = sessionId,
        ).set
        database.workoutSetDao().upsert(original)

        workoutRepository.setWorkoutSetSyncStatus(original.id, SyncStatus.SYNCED)

        val stored = database.workoutSetDao().getById(original.id)!!
        assertEquals(SyncStatus.SYNCED, stored.syncStatus)
        assertEquals(original.updatedAt, stored.updatedAt)
        assertEquals(original.weight, stored.weight)
        assertEquals(original, stored.copy(syncStatus = original.syncStatus))
    }

    @Test
    fun `recording a session sync leaves its status and timestamps untouched`() = runTest {
        val original = SyncEntityFixtures.session(
            routineId = null,
            status = WorkoutStatus.COMPLETED,
        )
        database.workoutSessionDao().upsert(original)

        workoutRepository.setWorkoutSessionSyncStatus(original.id, SyncStatus.FAILED)

        val stored = database.workoutSessionDao().getById(original.id)!!
        assertEquals(SyncStatus.FAILED, stored.syncStatus)
        assertEquals(WorkoutStatus.COMPLETED, stored.status)
        assertEquals(original.endedAt, stored.endedAt)
        assertEquals(original.updatedAt, stored.updatedAt)
    }

    @Test
    fun `a user edit after a successful sync makes the row pending again`() = runTest {
        // The complement of the loop guard: syncing must not re-dirty a row, but
        // a real edit must.
        val id = routineRepository.createRoutine("Push Day")
        routineRepository.setRoutineSyncStatus(id, SyncStatus.SYNCED)
        assertTrue(routineRepository.getPendingRoutines().isEmpty())

        routineRepository.renameRoutine(id, "Pull Day")

        assertEquals(listOf(id), routineRepository.getPendingRoutines().map { it.id })
    }
}

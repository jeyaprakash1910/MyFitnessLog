package com.myfitnesslog.core.sync.engine

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SyncStatus
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
 * Tests stranded-claim recovery against a real Room database.
 *
 * The scenario being defended against: the engine writes SYNCING, the process is
 * killed, and the row is then invisible to the pending queries forever. These
 * tests reproduce exactly that and prove the row comes back.
 */
@RunWith(RobolectricTestRunner::class)
class SyncRecoveryImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var recovery: SyncRecoveryImpl

    private val categoryId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()

    /** Far-future clock, so any accidental timestamp write is unmistakable. */
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
        recovery = SyncRecoveryImpl(
            routineSource = routineRepository,
            workoutSource = workoutRepository,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Seeds one row in every syncable table, each left stranded in SYNCING. */
    private suspend fun seedStrandedRowInEveryTable(): Int {
        val routine = SyncEntityFixtures.routine(syncStatus = SyncStatus.SYNCING)
        database.routineDao().upsert(routine)
        database.routineExerciseDao().upsert(
            SyncEntityFixtures.routineExercise(routineId = routine.id)
                .copy(exerciseId = exerciseId, syncStatus = SyncStatus.SYNCING),
        )

        val session = SyncEntityFixtures.session(routineId = null)
            .copy(syncStatus = SyncStatus.SYNCING)
        database.workoutSessionDao().upsert(session)
        val workoutExercise = SyncEntityFixtures.workoutExercise(sessionId = session.id)
            .copy(exerciseId = exerciseId, syncStatus = SyncStatus.SYNCING)
        database.workoutExerciseDao().upsert(workoutExercise)
        database.workoutSetDao().upsert(
            SyncEntityFixtures.pendingSet(
                workoutExerciseId = workoutExercise.id,
                sessionId = session.id,
            ).set.copy(syncStatus = SyncStatus.SYNCING),
        )
        return 5
    }

    @Test
    fun `a row stranded in SYNCING is invisible to pending work until recovered`() = runTest {
        val routine = SyncEntityFixtures.routine(syncStatus = SyncStatus.SYNCING)
        database.routineDao().upsert(routine)

        // This is the bug recovery exists to prevent.
        assertTrue(routineRepository.getPendingRoutines().isEmpty())

        recovery.recoverStaleSyncing()

        assertEquals(listOf(routine.id), routineRepository.getPendingRoutines().map { it.id })
    }

    @Test
    fun `recovery reclaims stranded rows across all five tables`() = runTest {
        val expected = seedStrandedRowInEveryTable()

        assertEquals(expected, recovery.recoverStaleSyncing())

        assertEquals(1, routineRepository.getPendingRoutines().size)
        assertEquals(1, routineRepository.getPendingRoutineExercises().size)
        assertEquals(1, workoutRepository.getPendingWorkoutSessions().size)
        assertEquals(1, workoutRepository.getPendingWorkoutExercises().size)
        assertEquals(1, workoutRepository.getPendingWorkoutSets().size)
    }

    @Test
    fun `recovery is idempotent`() = runTest {
        seedStrandedRowInEveryTable()

        assertEquals(5, recovery.recoverStaleSyncing())
        // Second run finds nothing left to reclaim and changes nothing.
        assertEquals(0, recovery.recoverStaleSyncing())
        assertEquals(0, recovery.recoverStaleSyncing())

        assertEquals(1, routineRepository.getPendingRoutines().size)
    }

    @Test
    fun `recovery on a clean database is a no-op`() = runTest {
        database.routineDao().upsert(SyncEntityFixtures.routine(syncStatus = SyncStatus.PENDING))

        assertEquals(0, recovery.recoverStaleSyncing())
    }

    @Test
    fun `recovery does not disturb SYNCED or FAILED rows`() = runTest {
        val synced = SyncEntityFixtures.routine(syncStatus = SyncStatus.SYNCED)
        val failed = SyncEntityFixtures.routine(syncStatus = SyncStatus.FAILED)
        val stranded = SyncEntityFixtures.routine(syncStatus = SyncStatus.SYNCING)
        listOf(synced, failed, stranded).forEach { database.routineDao().upsert(it) }

        assertEquals(1, recovery.recoverStaleSyncing())

        assertEquals(SyncStatus.SYNCED, database.routineDao().getById(synced.id)!!.syncStatus)
        assertEquals(SyncStatus.FAILED, database.routineDao().getById(failed.id)!!.syncStatus)
        assertEquals(SyncStatus.PENDING, database.routineDao().getById(stranded.id)!!.syncStatus)
    }

    @Test
    fun `recovery changes only syncStatus, never timestamps or business fields`() = runTest {
        val original = SyncEntityFixtures.routine(syncStatus = SyncStatus.SYNCING)
        database.routineDao().upsert(original)

        recovery.recoverStaleSyncing()

        val stored = database.routineDao().getById(original.id)!!
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertEquals(original.updatedAt, stored.updatedAt)
        assertEquals(original.createdAt, stored.createdAt)
        assertEquals(original.name, stored.name)
        assertEquals(original, stored.copy(syncStatus = original.syncStatus))
    }

    @Test
    fun `a stranded workout set keeps its measured values through recovery`() = runTest {
        val session = SyncEntityFixtures.session(routineId = null)
        database.workoutSessionDao().upsert(session)
        val workoutExercise = SyncEntityFixtures.workoutExercise(sessionId = session.id)
            .copy(exerciseId = exerciseId)
        database.workoutExerciseDao().upsert(workoutExercise)
        val original = SyncEntityFixtures.pendingSet(
            workoutExerciseId = workoutExercise.id,
            sessionId = session.id,
        ).set.copy(syncStatus = SyncStatus.SYNCING)
        database.workoutSetDao().upsert(original)

        recovery.recoverStaleSyncing()

        val stored = database.workoutSetDao().getById(original.id)!!
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertEquals(original.weight, stored.weight)
        assertEquals(original.updatedAt, stored.updatedAt)
        assertEquals(original, stored.copy(syncStatus = original.syncStatus))
    }
}

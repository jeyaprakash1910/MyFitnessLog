package com.myfitnesslog.feature.workout.domain

import android.database.sqlite.SQLiteConstraintException
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StartWorkoutUseCaseTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase

    @Before
    fun setUp() = runBlocking {
        database = newInMemoryDatabase()
        database.seedExercises()
        routineRepository = database.newRepository()
        startWorkout = StartWorkoutUseCase(
            syncTrigger = RecordingSyncTrigger(),
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            routineRepository = routineRepository,
            clock = RoutineTestData.clock,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun routineWithSquat(): Pair<java.util.UUID, java.util.UUID> {
        val routineId = routineRepository.createRoutine("Legs")
        val squatRe = routineRepository.addExercise(routineId, RoutineTestData.squatId, 5, 5, 5, 120, "heavy")
        return routineId to squatRe
    }

    @Test
    fun startCreatesInProgressSessionAndSnapshotsExercises() = runBlocking {
        val routineId = routineRepository.createRoutine("Legs")
        routineRepository.addExercise(routineId, RoutineTestData.squatId, 3, 8, 12, 90, null)
        routineRepository.addExercise(routineId, RoutineTestData.benchId, 3, 8, 12, 90, null)

        val sessionId = startWorkout(routineId)

        val session = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(WorkoutStatus.IN_PROGRESS, session.status)
        assertEquals(routineId, session.routineId)
        val snapshots = database.workoutExerciseDao().getBySession(sessionId)
        assertEquals(listOf("Squat", "Bench Press"), snapshots.map { it.exerciseName })
        assertEquals(listOf(0, 1), snapshots.map { it.exerciseOrder })
    }

    @Test
    fun snapshotCopiesAllTargetFields() = runBlocking {
        val (routineId, _) = routineWithSquat()

        val sessionId = startWorkout(routineId)

        val snapshot = database.workoutExerciseDao().getBySession(sessionId).single()
        assertEquals(RoutineTestData.squatId, snapshot.exerciseId)
        assertEquals("Squat", snapshot.exerciseName)
        assertEquals(5, snapshot.targetSets)
        assertEquals(5, snapshot.minTargetReps)
        assertEquals(5, snapshot.maxTargetReps)
        assertEquals(120, snapshot.targetRestSeconds)
        assertEquals("heavy", snapshot.notes)
    }

    @Test
    fun startingAgainResumesExistingSessionWithoutDuplicating() = runBlocking {
        val (routineId, _) = routineWithSquat()

        val first = startWorkout(routineId)
        val exercisesAfterFirst = database.workoutExerciseDao().getBySession(first).size
        val second = startWorkout(routineId)

        assertEquals(first, second)
        assertEquals(exercisesAfterFirst, database.workoutExerciseDao().getBySession(first).size)
    }

    @Test
    fun snapshotIsImmutableAfterRoutineIsEdited() = runBlocking {
        val (routineId, squatRe) = routineWithSquat()
        val sessionId = startWorkout(routineId)
        val before = database.workoutExerciseDao().getBySession(sessionId).single()

        // Mutate the routine after the workout started.
        routineRepository.updateExercise(squatRe, targetSets = 99, minTargetReps = 1, maxTargetReps = 2, targetRestSeconds = 10, notes = "changed")
        routineRepository.removeExercise(squatRe)
        routineRepository.renameRoutine(routineId, "Renamed")

        val after = database.workoutExerciseDao().getBySession(sessionId).single()
        assertEquals(before, after)
        assertNotNull(after.notes)
        assertEquals("heavy", after.notes)
        assertEquals(5, after.targetSets)
    }

    @Test
    fun startsWithEmptyRoutineAndCreatesNoExercises() = runBlocking {
        val routineId = routineRepository.createRoutine("Empty")

        val sessionId = startWorkout(routineId)

        assertEquals(WorkoutStatus.IN_PROGRESS, database.workoutSessionDao().getById(sessionId)!!.status)
        assertTrue(database.workoutExerciseDao().getBySession(sessionId).isEmpty())
    }

    @Test
    fun invalidRoutineThrowsAndLeavesNoActiveSession() = runBlocking {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { startWorkout(UUID.randomUUID()) }
        }

        // The transaction rolled back — no partial session remains.
        assertNull(database.workoutSessionDao().getActive())
    }

    @Test
    fun manualWorkoutHasNullRoutineAndNoExercises() = runBlocking {
        val sessionId = startWorkout(null)

        val session = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(WorkoutStatus.IN_PROGRESS, session.status)
        assertNull(session.routineId)
        assertTrue(database.workoutExerciseDao().getBySession(sessionId).isEmpty())
    }

    @Test
    fun manualStartRespectsSingleActiveInvariant() = runBlocking {
        val first = startWorkout(null)
        val second = startWorkout(null)
        assertEquals(first, second)
    }
}

package com.myfitnesslog.feature.workout.data

import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutRepositoryImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var repository: WorkoutRepositoryImpl
    private var sessionId: UUID = UUID.randomUUID()
    private var workoutExerciseId: UUID = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        database = newInMemoryDatabase()
        database.seedExercises()
        val routineRepository: RoutineRepositoryImpl = database.newRepository()
        val startWorkout = StartWorkoutUseCase(
            syncTrigger = RecordingSyncTrigger(),
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            routineRepository = routineRepository,
            clock = RoutineTestData.clock,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        val routineId = routineRepository.createRoutine("Legs")
        routineRepository.addExercise(routineId, RoutineTestData.squatId, 3, 8, 12, 90, null)
        sessionId = startWorkout(routineId)
        workoutExerciseId = database.workoutExerciseDao().getBySession(sessionId).single().id

        repository = WorkoutRepositoryImpl(
            syncTrigger = RecordingSyncTrigger(),
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = RoutineTestData.clock,
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addWorkingSet(weight: String = "80.00", reps: Int = 8) =
        repository.addSet(workoutExerciseId, BigDecimal(weight), reps)

    @Test
    fun addSetAppendsWithSequentialSetNumbers() = runBlocking {
        addWorkingSet()
        addWorkingSet(weight = "82.50")

        val sets = repository.observeSets(workoutExerciseId).first()
        assertEquals(listOf(1, 2), sets.map { it.setNumber })
        assertEquals(BigDecimal("82.50"), sets[1].weight)
    }

    @Test
    fun updateSetChangesValues() = runBlocking {
        val setId = addWorkingSet()

        repository.updateSet(setId, BigDecimal("85.00"), 10, SetCategory.TOP_SET, BigDecimal("9"), null, isCompleted = true)

        val set = repository.observeSets(workoutExerciseId).first().single()
        assertEquals(BigDecimal("85.00"), set.weight)
        assertEquals(10, set.repetitions)
        assertEquals(SetCategory.TOP_SET, set.setCategory)
    }

    @Test
    fun deleteSetRemovesItAndQueuesTheDeletionForUpload() = runBlocking {
        val setId = addWorkingSet()

        repository.deleteSet(setId)

        assertTrue(repository.observeSets(workoutExerciseId).first().isEmpty())
        // A set is hard-deleted, so without a tombstone the backend would never
        // learn of the deletion and would keep showing the set (ADR-0007).
        val tombstone = repository.getPendingWorkoutSetDeletions().single()
        assertEquals(setId, tombstone.workoutSetId)
        assertEquals(workoutExerciseId, tombstone.workoutExerciseId)
        assertEquals(sessionId, tombstone.workoutSessionId)
    }

    @Test
    fun clearingADeletionEmptiesTheQueue() = runBlocking {
        val setId = addWorkingSet()
        repository.deleteSet(setId)

        repository.clearWorkoutSetDeletion(setId)

        assertTrue(repository.getPendingWorkoutSetDeletions().isEmpty())
    }

    @Test
    fun deletingTheSameSetTwiceQueuesOneDeletion() = runBlocking {
        val setId = addWorkingSet()

        repository.deleteSet(setId)
        repository.deleteSet(setId) // the row is already gone — a no-op

        assertEquals(1, repository.getPendingWorkoutSetDeletions().size)
    }

    @Test
    fun completeWorkoutTransitionsAndClearsActive() = runBlocking {
        repository.completeWorkout(sessionId)

        assertNull(repository.observeActiveSession().first())
        val session = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(WorkoutStatus.COMPLETED, session.status)
        assertTrue(session.endedAt != null)
    }

    @Test
    fun discardWorkoutTransitions() = runBlocking {
        repository.discardWorkout(sessionId)

        val session = database.workoutSessionDao().getById(sessionId)!!
        assertEquals(WorkoutStatus.DISCARDED, session.status)
        assertNull(repository.observeActiveSession().first())
    }

    @Test
    fun completedWorkoutIsImmutable() = runBlocking {
        val setId = addWorkingSet()
        repository.completeWorkout(sessionId)

        assertThrows(IllegalStateException::class.java) { runBlocking { addWorkingSet() } }
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.updateSet(setId, BigDecimal("90.00"), 5, SetCategory.WORKING, null, null, true) }
        }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.deleteSet(setId) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.completeWorkout(sessionId) } }
        assertThrows(IllegalStateException::class.java) { runBlocking { repository.discardWorkout(sessionId) } }
        Unit
    }

    @Test
    fun rejectsNegativeWeight() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.addSet(workoutExerciseId, BigDecimal("-1"), 8) }
        }
        Unit
    }

    @Test
    fun addExerciseAppendsWithNameAndOrder() = runBlocking {
        // setUp started a workout from a routine containing "Squat" (order 0).
        repository.addExercise(sessionId, RoutineTestData.benchId, "Bench Press")

        val exercises = database.workoutExerciseDao().getBySession(sessionId)
        assertEquals(listOf("Squat", "Bench Press"), exercises.map { it.exerciseName })
        assertEquals(listOf(0, 1), exercises.map { it.exerciseOrder })
    }

    @Test
    fun addExerciseRejectedOnceCompleted() = runBlocking {
        repository.completeWorkout(sessionId)
        assertThrows(IllegalStateException::class.java) {
            runBlocking { repository.addExercise(sessionId, RoutineTestData.benchId, "Bench Press") }
        }
        Unit
    }
}

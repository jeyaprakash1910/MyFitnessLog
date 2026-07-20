package com.myfitnesslog.feature.history.data

import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.history.data.local.WorkoutHistoryDao
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.util.UUID

/**
 * Verifies the read-only history repository over a real in-memory Room database,
 * with workouts built through the production start/log/complete path so the
 * queries run against genuine snapshot rows.
 */
@RunWith(RobolectricTestRunner::class)
class WorkoutHistoryRepositoryImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var historyDao: WorkoutHistoryDao
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var repository: WorkoutHistoryRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        database = newInMemoryDatabase()
        database.seedExercises()
        historyDao = database.workoutHistoryDao()
        routineRepository = database.newRepository()
        startWorkout = StartWorkoutUseCase(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            routineRepository = routineRepository,
            clock = RoutineTestData.clock,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        workoutRepository = WorkoutRepositoryImpl(
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = RoutineTestData.clock,
        )
        repository = WorkoutHistoryRepositoryImpl(historyDao)
    }

    @After
    fun tearDown() = database.close()

    /** Starts a routine workout with one squat exercise and returns the session id. */
    private suspend fun startRoutineWorkout(): UUID {
        val routineId = routineRepository.createRoutine("Legs")
        routineRepository.addExercise(routineId, RoutineTestData.squatId, 3, 8, 12, 90, null)
        return startWorkout(routineId)
    }

    @Test
    fun completedWorkoutsExcludeInProgress() = runBlocking {
        startRoutineWorkout() // left IN_PROGRESS

        assertTrue(repository.observeCompletedWorkouts().first().isEmpty())
    }

    @Test
    fun completedWorkoutsExcludeDiscarded() = runBlocking {
        val sessionId = startRoutineWorkout()
        workoutRepository.discardWorkout(sessionId)

        assertTrue(repository.observeCompletedWorkouts().first().isEmpty())
    }

    @Test
    fun completedWorkoutAppearsInHistory() = runBlocking {
        val sessionId = startRoutineWorkout()
        workoutRepository.completeWorkout(sessionId)

        val history = repository.observeCompletedWorkouts().first()
        assertEquals(listOf(sessionId), history.map { it.id })
        assertEquals(WorkoutStatus.COMPLETED, history.single().status)
    }

    @Test
    fun observeWorkoutReturnsCompletedButNotDiscarded() = runBlocking {
        val completed = startRoutineWorkout()
        workoutRepository.completeWorkout(completed)
        val discarded = startRoutineWorkout()
        workoutRepository.discardWorkout(discarded)

        assertEquals(completed, repository.observeWorkout(completed).first()?.id)
        assertNull(repository.observeWorkout(discarded).first())
    }

    @Test
    fun observeExercisesAndSetsReturnSnapshotRows() = runBlocking {
        val sessionId = startRoutineWorkout()
        val workoutExerciseId = database.workoutExerciseDao().getBySession(sessionId).single().id
        workoutRepository.addSet(workoutExerciseId, BigDecimal("100.00"), 5)
        workoutRepository.addSet(workoutExerciseId, BigDecimal("100.00"), 5)
        workoutRepository.completeWorkout(sessionId)

        assertEquals(listOf("Squat"), repository.observeExercises(sessionId).first().map { it.exerciseName })
        val sets = repository.observeSets(sessionId).first()
        assertEquals(listOf(1, 2), sets.map { it.setNumber })
        assertTrue(sets.all { it.weight == BigDecimal("100.00") })
    }
}

package com.myfitnesslog.feature.history.ui

import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepositoryImpl
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.myfitnesslog.feature.routine.closeAndDrain
import com.myfitnesslog.feature.routine.tracked

@RunWith(RobolectricTestRunner::class)
class WorkoutHistoryViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase
    private lateinit var viewModel: WorkoutHistoryViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = newInMemoryDatabase()
        runBlocking { database.seedExercises() }
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
        workoutRepository = WorkoutRepositoryImpl(
            syncTrigger = RecordingSyncTrigger(),
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = RoutineTestData.clock,
        )
        viewModel = WorkoutHistoryViewModel(WorkoutHistoryRepositoryImpl(database.workoutHistoryDao())).tracked()
    }

    @After
    fun tearDown() {
        // Close the database before resetting Main, not after: Room's background
        // threads can otherwise resume a coroutine that reads the Main delegate
        // while resetMain replaces it, which throws "Dispatchers.Main is used
        // concurrently with setting it". TD-015, explained in full in
        // WorkoutViewModelTest.
        database.closeAndDrain()
        Dispatchers.resetMain()
    }

    private suspend fun startRoutineWorkout(): UUID {
        val routineId = routineRepository.createRoutine("Legs")
        routineRepository.addExercise(routineId, RoutineTestData.squatId, 3, 8, 12, 90, null)
        return startWorkout(routineId)
    }

    private suspend fun startManualWorkout(): UUID = startWorkout(null)

    @Test
    fun startsEmptyWhenNoCompletedWorkouts() = runBlocking {
        // An in-progress workout must not appear in history.
        startRoutineWorkout()
        assertEquals(
            WorkoutHistoryUiState.Empty,
            viewModel.uiState.awaitFirst { it is WorkoutHistoryUiState.Empty },
        )
    }

    @Test
    fun completedRoutineWorkoutAppearsWithRoutineIndicatorAndExerciseCount() = runBlocking {
        val sessionId = startRoutineWorkout()
        workoutRepository.completeWorkout(sessionId)

        val state = viewModel.uiState.awaitFirst {
            it is WorkoutHistoryUiState.Success
        } as WorkoutHistoryUiState.Success

        val item = state.workouts.single()
        assertEquals(sessionId, item.id)
        // The routine's name, snapshotted at start, is what the card is called.
        assertEquals("Legs", item.title)
        assertEquals("1 exercise", item.exercisesLabel)
        assertNull(item.notesPreview)
    }

    @Test
    fun manualWorkoutMapsToManualIndicator() = runBlocking {
        val sessionId = startManualWorkout()
        workoutRepository.completeWorkout(sessionId)

        val state = viewModel.uiState.awaitFirst {
            it is WorkoutHistoryUiState.Success
        } as WorkoutHistoryUiState.Success

        val item = state.workouts.single()
        // No routine, so no name was recorded: fall back rather than invent one.
        assertEquals("Manual Workout", item.title)
        assertEquals("0 exercises", item.exercisesLabel)
    }

    @Test
    fun multipleCompletedWorkoutsAllAppear() = runBlocking {
        // RoutineTestData.clock is fixed, so all sessions share startedAt here;
        // strict newest-first ordering (distinct startedAt) is verified in
        // WorkoutHistoryDaoTest. This asserts the ViewModel surfaces every
        // completed workout.
        val first = startRoutineWorkout()
        workoutRepository.completeWorkout(first)
        val second = startManualWorkout()
        workoutRepository.completeWorkout(second)

        val state = viewModel.uiState.awaitFirst {
            it is WorkoutHistoryUiState.Success && it.workouts.size == 2
        } as WorkoutHistoryUiState.Success

        assertEquals(setOf(first, second), state.workouts.map { it.id }.toSet())
    }
}

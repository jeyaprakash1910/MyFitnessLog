package com.myfitnesslog.feature.workout.ui.indicator

import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import com.myfitnesslog.feature.routine.closeAndDrain
import com.myfitnesslog.feature.routine.tracked

@RunWith(RobolectricTestRunner::class)
class WorkoutIndicatorViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase
    private var routineId: UUID = UUID.randomUUID()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = newInMemoryDatabase()
        database.seedExercises()
        routineRepository = database.newRepository()
        workoutRepository = WorkoutRepositoryImpl(
            syncTrigger = RecordingSyncTrigger(),
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = RoutineTestData.clock,
        )
        startWorkout = StartWorkoutUseCase(
            syncTrigger = RecordingSyncTrigger(),
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            routineRepository = routineRepository,
            clock = RoutineTestData.clock,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        routineId = routineRepository.createRoutine("Legs")
        routineRepository.addExercise(routineId, RoutineTestData.squatId, 3, 8, 12, 90, null)
        Unit
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

    private fun viewModel() =
        WorkoutIndicatorViewModel(workoutRepository, routineRepository, RoutineTestData.clock).tracked()

    @Test
    fun hiddenWhenNoActiveWorkout() = runBlocking {
        val state = viewModel().state.awaitFirst { it is WorkoutIndicatorUiState.Hidden }
        assertTrue(state is WorkoutIndicatorUiState.Hidden)
    }

    @Test
    fun visibleWhenAWorkoutIsActive() = runBlocking {
        startWorkout(routineId)
        val state = viewModel().state.awaitFirst { it is WorkoutIndicatorUiState.Visible }
        assertTrue(state is WorkoutIndicatorUiState.Visible)
    }

    @Test
    fun visibleCarriesRoutineNameAndCurrentExercise() = runBlocking {
        startWorkout(routineId)
        val state = viewModel().state.awaitFirst {
            it is WorkoutIndicatorUiState.Visible && it.currentExercise != null
        } as WorkoutIndicatorUiState.Visible
        assertEquals("Legs", state.routineName) // the source routine's name
        assertNotNull(state.currentExercise) // the exercise the user is on
    }

    @Test
    fun discardActiveWorkoutEndsTheWorkout() = runBlocking {
        startWorkout(routineId)
        val vm = viewModel()
        vm.state.awaitFirst { it is WorkoutIndicatorUiState.Visible }

        vm.discardActiveWorkout()

        // The session is discarded → no longer active → indicator hides.
        val state = vm.state.awaitFirst { it is WorkoutIndicatorUiState.Hidden }
        assertTrue(state is WorkoutIndicatorUiState.Hidden)
        assertNull(database.workoutSessionDao().getActive())
    }

    @Test
    fun observingTheIndicatorDoesNotChangeWorkoutState() = runBlocking {
        startWorkout(routineId)

        // Merely observing the indicator must not persist, sync, or mutate the session.
        viewModel().state.awaitFirst { it is WorkoutIndicatorUiState.Visible }

        val active = database.workoutSessionDao().getActive()
        assertNotNull(active) // still active — not completed/discarded by the indicator
        assertEquals(WorkoutStatus.IN_PROGRESS, active!!.status)
    }
}

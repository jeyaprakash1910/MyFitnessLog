package com.myfitnesslog.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkoutViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase
    private var routineId: UUID = UUID.randomUUID()
    private val clock = MutableClock(Instant.ofEpochMilli(1_700_000_000_000L))

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = newInMemoryDatabase()
        database.seedExercises()
        val routineRepository: RoutineRepositoryImpl = database.newRepository()
        workoutRepository = WorkoutRepositoryImpl(
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = RoutineTestData.clock,
        )
        startWorkout = StartWorkoutUseCase(
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
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel(startFrom: UUID? = routineId) = WorkoutViewModel(
        savedStateHandle = SavedStateHandle(
            if (startFrom != null) mapOf(WorkoutRoutes.ARG_ROUTINE_ID to startFrom.toString()) else emptyMap(),
        ),
        repository = workoutRepository,
        startWorkout = startWorkout,
        clock = clock,
    )

    private suspend fun WorkoutViewModel.awaitActive(predicate: (WorkoutUiState.Active) -> Boolean = { true }) =
        uiState.awaitFirst { it is WorkoutUiState.Active && predicate(it) } as WorkoutUiState.Active

    @Test
    fun startingFromRoutineShowsSnapshottedExercises() = runBlocking {
        val vm = viewModel()

        val state = vm.awaitActive { it.exercises.isNotEmpty() }
        assertEquals(listOf("Squat"), state.exercises.map { it.exerciseName })
        assertFalse(state.isReadOnly)
    }

    @Test
    fun resumesExistingWorkoutWhenNoRoutineProvided() = runBlocking {
        startWorkout(routineId) // create an active workout first

        val vm = viewModel(startFrom = null)

        val state = vm.awaitActive { it.exercises.isNotEmpty() }
        assertEquals(listOf("Squat"), state.exercises.map { it.exerciseName })
    }

    @Test
    fun addSetAppearsInState() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.awaitActive { it.exercises.isNotEmpty() }.exercises.first().id

        vm.addSet(exerciseId, BigDecimal("80"), 8, SetCategory.WORKING, null)

        val sets = vm.awaitActive { it.exercises.first().sets.isNotEmpty() }.exercises.first().sets
        assertEquals(1, sets.size)
        assertEquals(BigDecimal("80"), sets.single().weight)
    }

    @Test
    fun deleteSetRemovesIt() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.awaitActive { it.exercises.isNotEmpty() }.exercises.first().id
        vm.addSet(exerciseId, BigDecimal("80"), 8, SetCategory.WORKING, null)
        val setId = vm.awaitActive { it.exercises.first().sets.isNotEmpty() }.exercises.first().sets.single().id

        vm.deleteSet(setId)

        assertTrue(vm.awaitActive { it.exercises.first().sets.isEmpty() }.exercises.first().sets.isEmpty())
    }

    @Test
    fun toggleCompletionFlipsFlag() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.awaitActive { it.exercises.isNotEmpty() }.exercises.first().id
        vm.addSet(exerciseId, BigDecimal("80"), 8, SetCategory.WORKING, null)
        val set = vm.awaitActive { it.exercises.first().sets.isNotEmpty() }.exercises.first().sets.single()
        assertTrue(set.isCompleted)

        vm.toggleCompletion(set.id)

        val toggled = vm.awaitActive { it.exercises.first().sets.firstOrNull()?.isCompleted == false }
        assertFalse(toggled.exercises.first().sets.single().isCompleted)
    }

    @Test
    fun completeWorkoutEmitsEventAndBecomesReadOnly() = runBlocking {
        val vm = viewModel()
        vm.awaitActive { it.exercises.isNotEmpty() }
        val events = mutableListOf<WorkoutViewModel.Event>()
        val job = launch(Dispatchers.Main) { vm.events.collect(events::add) }

        vm.completeWorkout()

        val readOnly = vm.awaitActive { it.isReadOnly }
        assertTrue(readOnly.isReadOnly)
        assertTrue(events.contains(WorkoutViewModel.Event.COMPLETED))
        job.cancel()
    }

    @Test
    fun completedWorkoutRejectsFurtherEdits() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.awaitActive { it.exercises.isNotEmpty() }.exercises.first().id
        vm.completeWorkout()
        vm.awaitActive { it.isReadOnly }

        // Attempting to add a set is swallowed (repository rejects; VM catches).
        vm.addSet(exerciseId, BigDecimal("80"), 8, SetCategory.WORKING, null)

        assertTrue(database.workoutSetDao().getByExercise(exerciseId).isEmpty())
    }

    @Test
    fun discardWorkoutEmitsEvent() = runBlocking {
        val vm = viewModel()
        vm.awaitActive { it.exercises.isNotEmpty() }
        val events = mutableListOf<WorkoutViewModel.Event>()
        val job = launch(Dispatchers.Main) { vm.events.collect(events::add) }

        vm.discardWorkout()

        vm.awaitActive { it.isReadOnly }
        assertTrue(events.contains(WorkoutViewModel.Event.DISCARDED))
        job.cancel()
    }

    // --- Manual workouts ---

    @Test
    fun startManualWorkoutCreatesEmptyActiveWorkout() = runBlocking {
        val vm = viewModel(startFrom = null) // Workout tab, nothing active yet
        vm.uiState.awaitFirst { it is WorkoutUiState.NoActiveWorkout }

        vm.startManualWorkout()

        val state = vm.awaitActive()
        assertTrue(state.exercises.isEmpty())
    }

    // --- Timers ---

    @Test
    fun elapsedReflectsSessionStartedAt() = runBlocking {
        val vm = viewModel()
        vm.awaitActive { it.exercises.isNotEmpty() }
        clock.instant = clock.instant.plusSeconds(90)

        val elapsed = vm.elapsed.awaitFirst { it >= Duration.ofSeconds(90) }
        assertEquals(Duration.ofSeconds(90), elapsed)
    }

    @Test
    fun restTimerActionsUpdateState() = runBlocking {
        val vm = viewModel()
        vm.awaitActive { it.exercises.isNotEmpty() }

        vm.startRest(60)
        assertEquals(com.myfitnesslog.feature.workout.domain.RestTimerState.Running(60, 60), vm.restTimer.value)

        vm.skipRest()
        assertEquals(com.myfitnesslog.feature.workout.domain.RestTimerState.Finished, vm.restTimer.value)

        vm.cancelRest()
        assertEquals(com.myfitnesslog.feature.workout.domain.RestTimerState.Idle, vm.restTimer.value)
    }

    @Test
    fun completingWorkoutStopsRestTimer() = runBlocking {
        val vm = viewModel()
        vm.awaitActive { it.exercises.isNotEmpty() }
        vm.startRest(60)
        assertTrue(vm.restTimer.value is com.myfitnesslog.feature.workout.domain.RestTimerState.Running)

        vm.completeWorkout()

        assertEquals(
            com.myfitnesslog.feature.workout.domain.RestTimerState.Idle,
            vm.restTimer.awaitFirst { it == com.myfitnesslog.feature.workout.domain.RestTimerState.Idle },
        )
    }
}

/** A [Clock] whose instant can be advanced by tests. */
private class MutableClock(var instant: Instant) : Clock() {
    override fun instant(): Instant = instant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

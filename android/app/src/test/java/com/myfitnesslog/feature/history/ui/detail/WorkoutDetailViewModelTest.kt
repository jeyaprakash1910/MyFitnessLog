package com.myfitnesslog.feature.history.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepositoryImpl
import com.myfitnesslog.feature.history.ui.WorkoutHistoryRoutes
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutDetailViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase
    private lateinit var repository: WorkoutHistoryRepositoryImpl

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
        repository = WorkoutHistoryRepositoryImpl(database.workoutHistoryDao())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel(sessionId: UUID) = WorkoutDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(WorkoutHistoryRoutes.ARG_SESSION_ID to sessionId.toString())),
        repository = repository,
    )

    /** Starts a routine workout with one squat exercise; returns the session id. */
    private suspend fun startRoutineWorkout(): UUID {
        val routineId = routineRepository.createRoutine("Legs")
        routineRepository.addExercise(routineId, RoutineTestData.squatId, 3, 8, 12, 90, null)
        return startWorkout(routineId)
    }

    @Test
    fun notFoundWhenSessionNotCompleted() = runBlocking {
        val sessionId = startRoutineWorkout() // left IN_PROGRESS
        val state = viewModel(sessionId).uiState.awaitFirst { it !is WorkoutDetailUiState.Loading }
        assertEquals(WorkoutDetailUiState.NotFound, state)
    }

    @Test
    fun notFoundWhenDiscarded() = runBlocking {
        val sessionId = startRoutineWorkout()
        workoutRepository.discardWorkout(sessionId)
        val state = viewModel(sessionId).uiState.awaitFirst { it !is WorkoutDetailUiState.Loading }
        assertEquals(WorkoutDetailUiState.NotFound, state)
    }

    @Test
    fun mapsMetadataForCompletedRoutineWorkout() = runBlocking {
        val sessionId = startRoutineWorkout()
        workoutRepository.completeWorkout(sessionId)

        val state = viewModel(sessionId).uiState.awaitFirst {
            it is WorkoutDetailUiState.Success
        } as WorkoutDetailUiState.Success

        assertEquals("Routine Workout", state.typeLabel)
        assertEquals(1, state.exercises.size)
        assertEquals("Squat", state.exercises.single().name)
        assertEquals(1, state.exercises.single().position)
    }

    @Test
    fun groupsSetsUnderExerciseInOrder() = runBlocking {
        val sessionId = startRoutineWorkout()
        val squat = database.workoutExerciseDao().getBySession(sessionId).single().id
        workoutRepository.addSet(squat, BigDecimal("100.0"), 5, SetCategory.WORKING, BigDecimal("8"), null)
        workoutRepository.addSet(squat, BigDecimal("110.0"), 3, SetCategory.TOP_SET, null, null)
        // Add a second exercise with one set to prove grouping.
        val benchWe = workoutRepository.addExercise(sessionId, RoutineTestData.benchId, "Bench Press")
        workoutRepository.addSet(benchWe, BigDecimal("60.0"), 10)
        workoutRepository.completeWorkout(sessionId)

        val state = viewModel(sessionId).uiState.awaitFirst {
            it is WorkoutDetailUiState.Success && it.exercises.size == 2
        } as WorkoutDetailUiState.Success

        val squatRow = state.exercises[0]
        assertEquals("Squat", squatRow.name)
        assertEquals(listOf(1, 2), squatRow.sets.map { it.setNumber })
        assertEquals("100 × 5", squatRow.sets[0].weightReps)
        assertEquals("Working", squatRow.sets[0].category)
        assertEquals("RPE 8", squatRow.sets[0].rpe)
        assertEquals("Top set", squatRow.sets[1].category)

        val benchRow = state.exercises[1]
        assertEquals("Bench Press", benchRow.name)
        assertEquals(1, benchRow.sets.size)
    }
}

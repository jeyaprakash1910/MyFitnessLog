package com.myfitnesslog.feature.routine.ui

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.routine.ui.detail.RoutineDetailUiState
import com.myfitnesslog.feature.routine.ui.detail.RoutineDetailViewModel
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoutineDetailViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var repository: RoutineRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = newInMemoryDatabase()
        runBlocking { database.seedExercises() }
        repository = database.newRepository()
    }

    @After
    fun tearDown() {
        // Close the database before resetting Main, not after: Room's background
        // threads can otherwise resume a coroutine that reads the Main delegate
        // while resetMain replaces it, which throws "Dispatchers.Main is used
        // concurrently with setting it". TD-015, explained in full in
        // WorkoutViewModelTest.
        database.close()
        Dispatchers.resetMain()
    }

    private fun viewModelFor(routineId: UUID) = RoutineDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(RoutineRoutes.ARG_ROUTINE_ID to routineId.toString())),
        repository = repository,
    )

    @Test
    fun showsRoutineNameAndOrderedExercises() = runBlocking {
        val id = repository.createRoutine("Legs")
        repository.addExercise(id, RoutineTestData.squatId, 5, 5, 5, 120, null)
        repository.addExercise(id, RoutineTestData.benchId, 3, 8, 12, 90, null)
        val viewModel = viewModelFor(id)

        val state = viewModel.uiState.awaitFirst { it is RoutineDetailUiState.Success } as RoutineDetailUiState.Success
        assertEquals("Legs", state.name)
        assertEquals(listOf("Squat", "Bench Press"), state.exercises.map { it.exerciseName })
    }

    @Test
    fun unknownRoutineIsNotFound() = runBlocking {
        val viewModel = viewModelFor(UUID.randomUUID())

        assertEquals(
            RoutineDetailUiState.NotFound,
            viewModel.uiState.awaitFirst { it is RoutineDetailUiState.NotFound },
        )
    }
}

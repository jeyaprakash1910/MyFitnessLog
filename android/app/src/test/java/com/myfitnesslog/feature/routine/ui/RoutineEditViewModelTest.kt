package com.myfitnesslog.feature.routine.ui

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.routine.ui.edit.RoutineEditUiState
import com.myfitnesslog.feature.routine.ui.edit.RoutineEditViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
class RoutineEditViewModelTest {

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

    private fun viewModelFor(routineId: UUID) = RoutineEditViewModel(
        savedStateHandle = SavedStateHandle(mapOf(RoutineRoutes.ARG_ROUTINE_ID to routineId.toString())),
        repository = repository,
    )

    private suspend fun RoutineEditViewModel.awaitSuccess(predicate: (RoutineEditUiState.Success) -> Boolean = { true }) =
        uiState.awaitFirst { it is RoutineEditUiState.Success && predicate(it) } as RoutineEditUiState.Success

    @Test
    fun loadsNameAndExercises() = runBlocking {
        val id = repository.createRoutine("Legs")
        repository.addExercise(id, RoutineTestData.squatId, 3, 8, 12, 90, null)
        val viewModel = viewModelFor(id)

        val state = viewModel.awaitSuccess { it.exercises.isNotEmpty() }
        assertEquals("Legs", state.name)
        assertEquals(listOf("Squat"), state.exercises.map { it.exerciseName })
    }

    @Test
    fun onNameChangePersistsRename() = runBlocking {
        val id = repository.createRoutine("Legs")
        val viewModel = viewModelFor(id)
        viewModel.awaitSuccess()

        viewModel.onNameChange("Leg Day")

        assertEquals("Leg Day", viewModel.awaitSuccess { it.name == "Leg Day" }.name)
        assertEquals("Leg Day", repository.observeRoutine(id).first()?.name)
    }

    @Test
    fun removeExerciseRemovesRow() = runBlocking {
        val id = repository.createRoutine("Legs")
        val reId = repository.addExercise(id, RoutineTestData.squatId, 3, 8, 12, 90, null)
        val viewModel = viewModelFor(id)
        viewModel.awaitSuccess { it.exercises.isNotEmpty() }

        viewModel.removeExercise(reId)

        assertEquals(emptyList<String>(), viewModel.awaitSuccess { it.exercises.isEmpty() }.exercises.map { it.exerciseName })
    }

    @Test
    fun moveDownReordersExercises() = runBlocking {
        val id = repository.createRoutine("Legs")
        val squat = repository.addExercise(id, RoutineTestData.squatId, 3, 8, 12, 90, null)
        repository.addExercise(id, RoutineTestData.benchId, 3, 8, 12, 90, null)
        val viewModel = viewModelFor(id)
        viewModel.awaitSuccess { it.exercises.size == 2 }

        viewModel.moveDown(squat)

        val names = viewModel.awaitSuccess { it.exercises.firstOrNull()?.exerciseName == "Bench Press" }
            .exercises.map { it.exerciseName }
        assertEquals(listOf("Bench Press", "Squat"), names)
    }

    @Test
    fun updateTargetsChangesValues() = runBlocking {
        val id = repository.createRoutine("Legs")
        val reId = repository.addExercise(id, RoutineTestData.squatId, 3, 8, 12, 90, null)
        val viewModel = viewModelFor(id)
        viewModel.awaitSuccess { it.exercises.isNotEmpty() }

        viewModel.updateTargets(reId, targetSets = 5, minTargetReps = 5, maxTargetReps = 5, targetRestSeconds = 120, notes = null)

        val row = viewModel.awaitSuccess { it.exercises.firstOrNull()?.targetSets == 5 }.exercises.single()
        assertEquals(5, row.targetSets)
        assertEquals(5, row.minTargetReps)
        assertEquals(5, row.maxTargetReps)
    }
}

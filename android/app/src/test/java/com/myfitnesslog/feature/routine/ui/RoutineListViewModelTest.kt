package com.myfitnesslog.feature.routine.ui

import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.routine.ui.list.RoutineListUiState
import com.myfitnesslog.feature.routine.ui.list.RoutineListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutineListViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var repository: RoutineRepositoryImpl
    private lateinit var viewModel: RoutineListViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = newInMemoryDatabase()
        runBlocking { database.seedExercises() }
        repository = database.newRepository()
        viewModel = RoutineListViewModel(repository)
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

    @Test
    fun startsEmpty() = runBlocking {
        assertEquals(RoutineListUiState.Empty, viewModel.uiState.awaitFirst { it !is RoutineListUiState.Loading })
    }

    @Test
    fun createRoutineAddsItAndEmitsOpenEditor() = runBlocking {
        val events = mutableListOf<RoutineListViewModel.OpenEditor>()
        val job = launch(Dispatchers.Main) { viewModel.events.collect(events::add) }

        viewModel.createRoutine("Legs")

        val state = viewModel.uiState.awaitFirst { it is RoutineListUiState.Success } as RoutineListUiState.Success
        assertEquals(listOf("Legs"), state.routines.map { it.name })
        assertEquals(1, events.size)
        assertEquals(state.routines.single().id, events.single().routineId)
        job.cancel()
    }

    @Test
    fun deleteRoutineRemovesIt() = runBlocking {
        viewModel.createRoutine("Legs")
        val id = (viewModel.uiState.awaitFirst { it is RoutineListUiState.Success } as RoutineListUiState.Success)
            .routines.single().id

        viewModel.deleteRoutine(id)

        assertEquals(RoutineListUiState.Empty, viewModel.uiState.awaitFirst { it is RoutineListUiState.Empty })
    }

    @Test
    fun duplicateRoutineAddsACopy() = runBlocking {
        viewModel.createRoutine("Legs")
        val id = (viewModel.uiState.awaitFirst { it is RoutineListUiState.Success } as RoutineListUiState.Success)
            .routines.single().id

        viewModel.duplicateRoutine(id)

        val names = (viewModel.uiState.awaitFirst {
            it is RoutineListUiState.Success && it.routines.size == 2
        } as RoutineListUiState.Success).routines.map { it.name }
        assertTrue(names.contains("Legs"))
        assertTrue(names.contains("Legs (copy)"))
    }
}

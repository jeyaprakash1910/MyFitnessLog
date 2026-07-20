package com.myfitnesslog.feature.routine.ui

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class ExercisePickerViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var exerciseRepository: ExerciseRepository
    private var routineId: UUID = UUID.randomUUID()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = newInMemoryDatabase()
        runBlocking { database.seedExercises() }
        routineRepository = database.newRepository()
        exerciseRepository = DaoExerciseRepository(database)
        runBlocking { routineId = routineRepository.createRoutine("Legs") }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel() = ExercisePickerViewModel(
        savedStateHandle = SavedStateHandle(mapOf(RoutineRoutes.ARG_ROUTINE_ID to routineId.toString())),
        exerciseRepository = exerciseRepository,
        routineRepository = routineRepository,
    )

    @Test
    fun listsAllExercisesOrdered() = runBlocking {
        val vm = viewModel()
        val state = vm.uiState.awaitFirst { it.exercises.isNotEmpty() }
        assertEquals(listOf("Bench Press", "Squat"), state.exercises.map { it.name })
    }

    @Test
    fun searchFiltersLocally() = runBlocking {
        val vm = viewModel()
        vm.uiState.awaitFirst { it.exercises.size == 2 }

        vm.onQueryChange("squat")

        val state = vm.uiState.awaitFirst { it.query == "squat" && it.exercises.size == 1 }
        assertEquals(listOf("Squat"), state.exercises.map { it.name })
    }

    @Test
    fun selectingExerciseAddsItToRoutineAndEmitsAdded() = runBlocking {
        val vm = viewModel()
        val events = mutableListOf<ExercisePickerViewModel.Added>()
        val job = launch(Dispatchers.Main) { vm.events.collect(events::add) }
        val squat = vm.uiState.awaitFirst { it.exercises.isNotEmpty() }.exercises.first { it.name == "Squat" }

        vm.onExerciseSelected(squat.id)

        val added = routineRepository.observeRoutineExercises(routineId).awaitFirst { it.isNotEmpty() }
        assertEquals(listOf("Squat"), added.map { it.exerciseName })
        assertTrue(events.isNotEmpty())
        job.cancel()
    }
}

/** Minimal [ExerciseRepository] backed directly by the in-memory DAO. */
private class DaoExerciseRepository(
    private val database: MyFitnessLogDatabase,
) : ExerciseRepository {
    override fun observeAll(): Flow<List<ExerciseEntity>> = database.exerciseDao().observeAll()
    override fun observeById(id: UUID): Flow<ExerciseEntity?> = database.exerciseDao().observeById(id)
    override fun observeFiltered(categoryId: UUID?, query: String?): Flow<List<ExerciseEntity>> =
        database.exerciseDao().observeFiltered(categoryId, query)
    override suspend fun refresh() = Unit
}

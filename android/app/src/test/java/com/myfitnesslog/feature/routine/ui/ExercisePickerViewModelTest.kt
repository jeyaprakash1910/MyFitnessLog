package com.myfitnesslog.feature.routine.ui

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerViewModel
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import com.myfitnesslog.feature.workout.ui.WorkoutRoutes
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
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var exerciseRepository: ExerciseRepository
    private lateinit var startWorkout: StartWorkoutUseCase
    private var routineId: UUID = UUID.randomUUID()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        database = newInMemoryDatabase()
        runBlocking { database.seedExercises() }
        routineRepository = database.newRepository()
        workoutRepository = WorkoutRepositoryImpl(
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = RoutineTestData.clock,
        )
        exerciseRepository = DaoExerciseRepository(database)
        startWorkout = StartWorkoutUseCase(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            routineRepository = routineRepository,
            clock = RoutineTestData.clock,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
        runBlocking { routineId = routineRepository.createRoutine("Legs") }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        database.close()
    }

    private fun viewModel(args: Map<String, String>) = ExercisePickerViewModel(
        savedStateHandle = SavedStateHandle(args),
        exerciseRepository = exerciseRepository,
        routineRepository = routineRepository,
        workoutRepository = workoutRepository,
    )

    private fun routineViewModel() = viewModel(mapOf(RoutineRoutes.ARG_ROUTINE_ID to routineId.toString()))

    @Test
    fun listsAllExercisesOrdered() = runBlocking {
        val vm = routineViewModel()
        val state = vm.uiState.awaitFirst { it.exercises.isNotEmpty() }
        assertEquals(listOf("Bench Press", "Squat"), state.exercises.map { it.name })
    }

    @Test
    fun searchFiltersLocally() = runBlocking {
        val vm = routineViewModel()
        vm.uiState.awaitFirst { it.exercises.size == 2 }
        vm.onQueryChange("squat")
        val state = vm.uiState.awaitFirst { it.query == "squat" && it.exercises.size == 1 }
        assertEquals(listOf("Squat"), state.exercises.map { it.name })
    }

    @Test
    fun selectingExerciseAddsToRoutine() = runBlocking {
        val vm = routineViewModel()
        val events = mutableListOf<ExercisePickerViewModel.Added>()
        val job = launch(Dispatchers.Main) { vm.events.collect(events::add) }
        val squat = vm.uiState.awaitFirst { it.exercises.isNotEmpty() }.exercises.first { it.name == "Squat" }

        vm.onExerciseSelected(squat.id)

        val added = routineRepository.observeRoutineExercises(routineId).awaitFirst { it.isNotEmpty() }
        assertEquals(listOf("Squat"), added.map { it.exerciseName })
        assertTrue(events.isNotEmpty())
        job.cancel()
    }

    @Test
    fun selectingExerciseAddsToManualWorkout() = runBlocking {
        val sessionId = startWorkout(null) // manual workout
        val vm = viewModel(mapOf(WorkoutRoutes.ARG_SESSION_ID to sessionId.toString()))
        val squat = vm.uiState.awaitFirst { it.exercises.isNotEmpty() }.exercises.first { it.name == "Squat" }

        vm.onExerciseSelected(squat.id)

        val added = workoutRepository.observeWorkoutExercises(sessionId).awaitFirst { it.isNotEmpty() }
        assertEquals(listOf("Squat"), added.map { it.exerciseName })
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

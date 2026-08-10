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
import com.myfitnesslog.core.data.local.SyncStatus
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.myfitnesslog.feature.routine.closeAndDrain
import com.myfitnesslog.feature.routine.tracked

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
        // Close the database before resetting Main, not after: Room's background
        // threads can otherwise resume a coroutine that reads the Main delegate
        // while resetMain replaces it, which throws "Dispatchers.Main is used
        // concurrently with setting it". TD-015, explained in full in
        // WorkoutViewModelTest.
        database.closeAndDrain()
        Dispatchers.resetMain()
    }

    private fun viewModel(sessionId: UUID) = WorkoutDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(WorkoutHistoryRoutes.ARG_SESSION_ID to sessionId.toString())),
        repository = repository,
        workoutRepository = workoutRepository,
    ).tracked()

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

    // --- Corrections (ADR-0018) --------------------------------------------

    /** Completes a workout with one squat set and returns (sessionId, setId). */
    private suspend fun completedWorkoutWithOneSet(): Pair<UUID, UUID> {
        val sessionId = startRoutineWorkout()
        val squat = database.workoutExerciseDao().getBySession(sessionId).single().id
        workoutRepository.addSet(squat, BigDecimal("100.0"), 5, SetCategory.WORKING, BigDecimal("8"), null)
        workoutRepository.completeWorkout(sessionId)
        return sessionId to database.workoutSetDao().getByExercise(squat).single().id
    }

    private suspend fun WorkoutDetailViewModel.awaitSuccess(
        predicate: (WorkoutDetailUiState.Success) -> Boolean = { true },
    ) = uiState.awaitFirst {
        it is WorkoutDetailUiState.Success && predicate(it)
    } as WorkoutDetailUiState.Success

    private fun WorkoutDetailUiState.Success.set(setId: UUID) =
        exercises.flatMap { it.sets }.first { it.id == setId }

    @Test
    fun openingACorrectionPreFillsWhatWasRecorded() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)

        val correction = vm.awaitSuccess().correction!!
        assertEquals("100", correction.weight)
        assertEquals("5", correction.repetitions)
        assertEquals("8", correction.rpe)
        assertEquals("Squat", correction.exerciseName)
        assertEquals(1, correction.setNumber)
    }

    /**
     * The correction this feature exists for: 100 was logged, 105 was lifted.
     *
     * Asserts the persisted row rather than the dialog closing, and asserts it is
     * PENDING: a correction that never uploads is not a correction, and Stage 2's
     * refresh would be free to overwrite it with the stale server copy.
     */
    @Test
    fun correctingAWeightPersistsItAndQueuesItForUpload() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onCorrectionWeightChange("105")
        vm.onCorrectionRepsChange("4")
        vm.onCorrectionSaved()

        // Await the effect rather than assuming the write is synchronous: it runs
        // in viewModelScope and lands via a Room emission.
        vm.awaitSuccess { it.set(setId).repetitions == 4 }
        val stored = database.workoutSetDao().getById(setId)!!
        assertEquals(0, BigDecimal("105").compareTo(stored.weight))
        assertEquals(4, stored.repetitions)
        assertEquals(SyncStatus.PENDING, stored.syncStatus)
        assertNull("the dialog should close on success", vm.awaitSuccess().correction)
    }

    /** An RPE entered by mistake is removed by clearing the field. */
    @Test
    fun clearingRpeRemovesIt() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onCorrectionRpeChange("")
        vm.onCorrectionSaved()

        vm.awaitSuccess { it.set(setId).rpeValue == null }
        assertNull(database.workoutSetDao().getById(setId)!!.rpe)
    }

    /**
     * Fields the dialog does not offer must survive the correction. Sending a
     * default back would quietly reclassify a top set as working, or drop an RIR.
     */
    @Test
    fun correctionPreservesFieldsTheDialogDoesNotOffer() = runBlocking {
        val sessionId = startRoutineWorkout()
        val squat = database.workoutExerciseDao().getBySession(sessionId).single().id
        workoutRepository.addSet(squat, BigDecimal("140.0"), 1, SetCategory.TOP_SET, null, BigDecimal("2"))
        workoutRepository.completeWorkout(sessionId)
        val setId = database.workoutSetDao().getByExercise(squat).single().id

        val vm = viewModel(sessionId)
        vm.awaitSuccess()
        vm.onCorrectSet(setId)
        vm.onCorrectionWeightChange("145")
        vm.onCorrectionSaved()

        vm.awaitSuccess { it.set(setId).weight.compareTo(BigDecimal("145")) == 0 }
        val stored = database.workoutSetDao().getById(setId)!!
        assertEquals(SetCategory.TOP_SET, stored.setCategory)
        assertEquals(0, BigDecimal("2").compareTo(stored.rir!!))
    }

    /**
     * Unusable input keeps the dialog open with a message rather than closing and
     * discarding the edit. A half-typed number is the user mid-thought.
     */
    @Test
    fun unusableInputKeepsTheDialogOpenAndChangesNothing() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()
        vm.onCorrectSet(setId)

        vm.onCorrectionRepsChange("0")
        vm.onCorrectionSaved()

        val correction = vm.awaitSuccess().correction
        assertNotNull("dialog must stay open", correction)
        assertNotNull("and say why", correction!!.error)
        // Nothing was written.
        assertEquals(5, database.workoutSetDao().getById(setId)!!.repetitions)
    }

    @Test
    fun anOutOfRangeRpeIsRejected() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()
        vm.onCorrectSet(setId)

        vm.onCorrectionRpeChange("11")
        vm.onCorrectionSaved()

        assertNotNull(vm.awaitSuccess().correction?.error)
        assertEquals(0, BigDecimal("8").compareTo(database.workoutSetDao().getById(setId)!!.rpe!!))
    }

    @Test
    fun cancellingDiscardsTheEdit() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onCorrectionWeightChange("999")
        vm.onCorrectionDismissed()

        assertNull(vm.awaitSuccess().correction)
        assertEquals(0, BigDecimal("100.0").compareTo(database.workoutSetDao().getById(setId)!!.weight))
    }
}

package com.myfitnesslog.feature.history.ui.detail

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepositoryImpl
import com.myfitnesslog.feature.history.ui.WorkoutHistoryRoutes
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.awaitWork
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.workout.data.SetWriteIntent
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

    /**
     * Awaits the dialog being open, rather than sampling whatever state is current.
     *
     * `awaitSuccess()` with no predicate returns the *first* Success it sees, which
     * may still be the one from before the action under test. `correction` is a
     * separate flow combined into `uiState`, so opening a dialog is not visible in
     * the combined state until it recomputes, and reading `.correction!!` straight
     * after the call is a race that throws NPE when it loses. It lost on CI on
     * 2026-08-12 having passed locally and on the previous CI run.
     *
     * Every assertion about a dialog therefore waits for the property it is about.
     */
    private suspend fun WorkoutDetailViewModel.awaitCorrection(
        predicate: (SetCorrection) -> Boolean = { true },
    ) = awaitSuccess { it.correction?.let(predicate) == true }.correction!!

    /** Awaits the dialog being closed, for the same reason. */
    private suspend fun WorkoutDetailViewModel.awaitNoCorrection() =
        awaitSuccess { it.correction == null }

    /**
     * Awaits the discard confirmation reaching [expected], for the same reason as
     * [awaitCorrection] - and it is the same bug, found again by TD-019.
     *
     * `confirmingDiscard` is a separate [kotlinx.coroutines.flow.MutableStateFlow]
     * combined into `uiState`, and `onDiscardRequested` sets it and launches
     * nothing. So neither of the two obvious guards works: a bare `awaitSuccess()`
     * returns the *current* value, which is still the state from before the call,
     * and wrapping the call in `awaitWork` joins zero coroutines and returns
     * immediately, which looks careful and waits for nothing at all.
     *
     * Only the combine recomputing makes the flag visible, so the wait has to be
     * for the flag.
     */
    private suspend fun WorkoutDetailViewModel.awaitConfirmingDiscard(expected: Boolean) =
        awaitSuccess { it.confirmingDiscard == expected }

    private fun WorkoutDetailUiState.Success.set(setId: UUID) =
        exercises.flatMap { it.sets }.first { it.id == setId }

    @Test
    fun openingACorrectionPreFillsWhatWasRecorded() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)

        val correction = vm.awaitCorrection()
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
        assertNull("the dialog should close on success", vm.awaitNoCorrection().correction)
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
        vm.awaitWork { vm.onCorrectionSaved() }

        val correction = vm.awaitCorrection { it.error != null }
        assertNotNull("dialog must stay open", correction)
        assertNotNull("and say why", correction.error)
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
        // awaitWork before a "nothing was written" assertion: an absence cannot be
        // waited for, only confirmed once any launched work has finished.
        vm.awaitWork { vm.onCorrectionSaved() }

        assertNotNull(vm.awaitCorrection { it.error != null }.error)
        assertEquals(0, BigDecimal("8").compareTo(database.workoutSetDao().getById(setId)!!.rpe!!))
    }

    // --- Adding a set that was performed but never logged -------------------

    /**
     * The second correction ADR-0018 permits. The backend and the repository have
     * allowed this on a COMPLETED session since 2026-08-08; until 2026-08-12 the
     * detail screen offered no way to reach it.
     */
    @Test
    fun addingAForgottenSetPersistsItAndQueuesItForUpload() = runBlocking {
        val (sessionId, _) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        val exerciseId = vm.awaitSuccess().exercises.single().id

        vm.onAddSet(exerciseId)
        vm.onCorrectionWeightChange("102.5")
        vm.onCorrectionRepsChange("3")
        vm.onCorrectionRpeChange("9")
        vm.onCorrectionSaved()

        val state = vm.awaitSuccess { it.exercises.single().sets.size == 2 }
        val added = state.exercises.single().sets.last()
        assertEquals(2, added.setNumber)
        assertEquals(0, BigDecimal("102.5").compareTo(added.weight))
        assertEquals(3, added.repetitions)
        assertEquals(SyncStatus.PENDING, database.workoutSetDao().getById(added.id)!!.syncStatus)
    }

    /** The dialog names the set about to be created, matching the repository's numbering. */
    @Test
    fun theAddDialogOpensEmptyAndOnTheNextSetNumber() = runBlocking {
        val (sessionId, _) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        val exerciseId = vm.awaitSuccess().exercises.single().id

        vm.onAddSet(exerciseId)

        val correction = vm.awaitCorrection()
        assertEquals(2, correction.setNumber)
        assertEquals("Squat", correction.exerciseName)
        assertNull("a new set has no row yet", correction.setId)
        // Empty rather than pre-filled from the previous set: a plausible guess is
        // the value a user accepts without reading.
        assertEquals("", correction.weight)
        assertEquals("", correction.repetitions)
    }

    /** Validation is the same rule for adding as for editing, and nothing is written. */
    @Test
    fun anAddWithUnusableInputKeepsTheDialogOpenAndWritesNothing() = runBlocking {
        val (sessionId, _) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        val exerciseId = vm.awaitSuccess().exercises.single().id

        vm.onAddSet(exerciseId)
        vm.onCorrectionWeightChange("80")
        // Reps left empty.
        vm.awaitWork { vm.onCorrectionSaved() }

        val correction = vm.awaitCorrection { it.error != null }
        assertNotNull("dialog must stay open", correction)
        assertNotNull("and say why", correction.error)
        assertEquals(1, database.workoutSetDao().getByExercise(exerciseId).size)
    }

    // --- Deleting a set that was logged but not performed -------------------

    /**
     * Asserts the tombstone as well as the removal. A delete that does not write
     * one is invisible to the backend (ADR-0007), which is the exact divergence
     * this correction exists to avoid.
     */
    @Test
    fun deletingASetRemovesItAndTombstonesItForUpload() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onDeleteRequested()
        vm.onDeleteConfirmed()

        vm.awaitSuccess { it.exercises.single().sets.isEmpty() }
        assertNull(database.workoutSetDao().getById(setId))
        assertEquals(
            listOf(setId),
            database.workoutSetDao().getPendingTombstones().map { it.workoutSetId },
        )
    }

    /**
     * Deleting from the middle must not leave history reading "Set 1, Set 3".
     *
     * Numbering is closed at display time, matching what the in-progress screen
     * already does in WorkoutRowMerger. The stored numbers deliberately keep their
     * gap: during logging they are the slot an undone set falls back into, and
     * rewriting them would dirty rows the user never edited.
     */
    @Test
    fun deletingAMiddleSetLeavesNoGapInTheDisplayedNumbering() = runBlocking {
        val sessionId = startRoutineWorkout()
        val squat = database.workoutExerciseDao().getBySession(sessionId).single().id
        workoutRepository.addSet(squat, BigDecimal("100.0"), 5, SetCategory.WORKING, null, null)
        workoutRepository.addSet(squat, BigDecimal("110.0"), 4, SetCategory.WORKING, null, null)
        workoutRepository.addSet(squat, BigDecimal("120.0"), 3, SetCategory.WORKING, null, null)
        workoutRepository.completeWorkout(sessionId)
        val middle = database.workoutSetDao().getByExercise(squat)[1].id

        val vm = viewModel(sessionId)
        vm.awaitSuccess { it.exercises.single().sets.size == 3 }
        vm.onCorrectSet(middle)
        vm.onDeleteRequested()
        vm.onDeleteConfirmed()

        val sets = vm.awaitSuccess { it.exercises.single().sets.size == 2 }.exercises.single().sets
        assertEquals(listOf(1, 2), sets.map { it.setNumber })
        // The surviving rows are the first and third, in order, unmodified.
        assertEquals(0, BigDecimal("100.0").compareTo(sets[0].weight))
        assertEquals(0, BigDecimal("120.0").compareTo(sets[1].weight))
        // Storage keeps its gap: nothing was rewritten to achieve the display.
        assertEquals(listOf(1, 3), database.workoutSetDao().getByExercise(squat).map { it.setNumber })
    }

    /** The add dialog names the next position, not the next stored number. */
    @Test
    fun addingAfterADeletionNamesTheNextDisplayedPosition() = runBlocking {
        val sessionId = startRoutineWorkout()
        val squat = database.workoutExerciseDao().getBySession(sessionId).single().id
        workoutRepository.addSet(squat, BigDecimal("100.0"), 5, SetCategory.WORKING, null, null)
        workoutRepository.addSet(squat, BigDecimal("110.0"), 4, SetCategory.WORKING, null, null)
        workoutRepository.completeWorkout(sessionId)
        val last = database.workoutSetDao().getByExercise(squat)[1].id
        // CORRECTION: the session is COMPLETED, and a logging write would be refused.
        workoutRepository.deleteSet(last, intent = SetWriteIntent.CORRECTION)
        // stored numbers are now [1], so the next stored number would be 3

        val vm = viewModel(sessionId)
        vm.awaitSuccess { it.exercises.single().sets.size == 1 }
        vm.onAddSet(squat)

        assertEquals(2, vm.awaitCorrection().setNumber)
    }

    /** A single tap must not destroy a record: delete asks first. */
    @Test
    fun deleteAsksBeforeItRemovesAnything() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onDeleteRequested()

        assertEquals(true, vm.awaitCorrection { it.confirmingDelete }.confirmingDelete)
        assertNotNull("nothing is deleted until confirmed", database.workoutSetDao().getById(setId))
    }

    @Test
    fun backingOutOfDeleteKeepsTheSetAndReturnsToTheEditFields() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onDeleteRequested()
        vm.onDeleteCancelled()

        val correction = vm.awaitCorrection { !it.confirmingDelete }
        assertEquals(false, correction.confirmingDelete)
        assertNotNull(database.workoutSetDao().getById(setId))
    }

    // --- Discarding the whole workout ---------------------------------------

    /** Removing a workout from history is one confirmed action away, and no more. */
    @Test
    fun discardingRemovesTheWorkoutFromHistory() = runBlocking {
        val (sessionId, _) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onDiscardRequested()
        assertEquals(true, vm.awaitConfirmingDiscard(true).confirmingDiscard)
        vm.awaitWork { vm.onDiscardConfirmed() }

        // The history projection stops resolving it, which is what "removed from
        // history" means: COMPLETED only, on every client and on the backend.
        assertEquals(WorkoutDetailUiState.NotFound, vm.uiState.awaitFirst { it is WorkoutDetailUiState.NotFound })
        assertEquals(WorkoutStatus.DISCARDED, database.workoutSessionDao().getById(sessionId)!!.status)
    }

    /**
     * The screen is told to leave, and only after the write.
     *
     * Signalling before it would navigate away from a discard that failed, leaving
     * the workout in history and the user with no idea the action did nothing.
     */
    @Test
    fun discardingSignalsTheScreenToLeaveOnlyAfterTheWriteLands() = runBlocking {
        val (sessionId, _) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        assertEquals(false, vm.discarded.value)
        vm.onDiscardRequested()
        assertEquals(false, vm.discarded.value)

        vm.awaitWork { vm.onDiscardConfirmed() }

        assertEquals(true, vm.discarded.value)
        assertEquals(WorkoutStatus.DISCARDED, database.workoutSessionDao().getById(sessionId)!!.status)
    }

    /** Merely opening the workout must not send anyone anywhere. */
    @Test
    fun theScreenIsNotToldToLeaveWithoutADiscard() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onCorrectionWeightChange("105")
        vm.awaitWork { vm.onCorrectionSaved() }

        assertEquals(false, vm.discarded.value)
    }

    /** A single tap must not remove a workout: it asks first. */
    @Test
    fun discardAsksBeforeItRemovesAnything() = runBlocking {
        val (sessionId, _) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onDiscardRequested()

        assertEquals(true, vm.awaitConfirmingDiscard(true).confirmingDiscard)
        assertEquals(WorkoutStatus.COMPLETED, database.workoutSessionDao().getById(sessionId)!!.status)
    }

    @Test
    fun backingOutOfDiscardKeepsTheWorkout() = runBlocking {
        val (sessionId, _) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onDiscardRequested()
        vm.awaitConfirmingDiscard(true)
        vm.onDiscardCancelled()

        assertEquals(false, vm.awaitConfirmingDiscard(false).confirmingDiscard)
        assertEquals(WorkoutStatus.COMPLETED, database.workoutSessionDao().getById(sessionId)!!.status)
    }

    /**
     * Nothing is destroyed. DISCARDED is a status, so the sets remain and the
     * workout is recoverable through the API even though no client offers it.
     */
    @Test
    fun discardingDestroysNothing() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onDiscardRequested()
        vm.awaitWork { vm.onDiscardConfirmed() }

        assertNotNull(database.workoutSetDao().getById(setId))
    }

    @Test
    fun cancellingDiscardsTheEdit() = runBlocking {
        val (sessionId, setId) = completedWorkoutWithOneSet()
        val vm = viewModel(sessionId)
        vm.awaitSuccess()

        vm.onCorrectSet(setId)
        vm.onCorrectionWeightChange("999")
        vm.onCorrectionDismissed()

        assertNull(vm.awaitNoCorrection().correction)
        assertEquals(0, BigDecimal("100.0").compareTo(database.workoutSetDao().getById(setId)!!.weight))
    }
}

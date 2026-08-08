package com.myfitnesslog.feature.workout.ui

import androidx.lifecycle.SavedStateHandle
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.routine.RoutineTestData
import com.myfitnesslog.feature.routine.awaitFirst
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.routine.newInMemoryDatabase
import com.myfitnesslog.feature.routine.newRepository
import com.myfitnesslog.feature.routine.seedExercises
import com.myfitnesslog.feature.history.data.WorkoutHistoryRepositoryImpl
import com.myfitnesslog.feature.settings.data.SettingsRepository
import com.myfitnesslog.feature.settings.domain.PreviousWorkoutValues
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import java.math.BigDecimal
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutViewModelTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var historyRepository: WorkoutHistoryRepositoryImpl
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
            syncTrigger = RecordingSyncTrigger(),
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = RoutineTestData.clock,
        )
        historyRepository = WorkoutHistoryRepositoryImpl(database.workoutHistoryDao())
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

    /**
     * TD-015. Close the database **before** resetting Main, not after.
     *
     * The collision this avoids: Room dispatches queries and invalidation
     * refreshes on its own background threads, so an emission can resume a
     * coroutine that reads the `Dispatchers.Main` delegate at the very moment
     * `resetMain()` replaces it, and kotlinx's concurrency check throws
     * "Dispatchers.Main is used concurrently with setting it".
     *
     * The window is not the five seconds it looks like. `uiState` is shared with
     * `SharingStarted.WhileSubscribed(5_000)`, and that stop timeout is a `delay`
     * on the test dispatcher's **virtual** clock, which nothing here advances. So
     * the timeout never expires and the Room flows underneath are still being
     * collected on Main when teardown arrives, every time.
     *
     * Closing the database first shuts down Room's invalidation tracker and
     * executors, so there is no longer anything that can touch Main. Reversing the
     * two lines is the whole fix.
     *
     * What deliberately does **not** happen here is cancelling the ViewModel
     * scopes or draining the dispatcher first. Both were measured and both made
     * things worse, for the same reason: cancellation and draining are themselves
     * work dispatched onto the dispatcher being removed, so the cleanup causes the
     * collision it was meant to prevent. One took the flake from about one run in
     * four to eight in eight.
     */
    @After
    fun tearDown() {
        database.close()
        Dispatchers.resetMain()
    }

    private fun newRestTimer() = com.myfitnesslog.feature.workout.domain.RestTimer(
        kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher()),
    )

    private fun viewModel(
        startFrom: UUID? = routineId,
        restTimer: com.myfitnesslog.feature.workout.domain.RestTimer = newRestTimer(),
        previousStrategy: PreviousWorkoutValues = PreviousWorkoutValues.ANY_WORKOUT,
    ) = WorkoutViewModel(
        savedStateHandle = SavedStateHandle(
            if (startFrom != null) mapOf(WorkoutRoutes.ARG_ROUTINE_ID to startFrom.toString()) else emptyMap(),
        ),
        repository = workoutRepository,
        historyRepository = historyRepository,
        settingsRepository = FakeSettingsRepository(previousStrategy),
        startWorkout = startWorkout,
        clock = clock,
        restTimerController = restTimer,
    )

    /** Minimal in-memory [SettingsRepository] for choosing the PREVIOUS strategy under test. */
    private class FakeSettingsRepository(strategy: PreviousWorkoutValues) : SettingsRepository {
        private val state = kotlinx.coroutines.flow.MutableStateFlow(strategy)
        override val previousWorkoutValues = state
        override suspend fun setPreviousWorkoutValues(value: PreviousWorkoutValues) { state.value = value }
    }

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

    /** Completes the first still-planned row of the first exercise with the given values. */
    private suspend fun WorkoutViewModel.completeFirstPlanned(weight: String, reps: String): UUID {
        val ex = awaitActive { it.exercises.firstOrNull()?.rows?.any { r -> !r.isCompleted } == true }.exercises.first()
        val plannedKey = ex.rows.first { !it.isCompleted }.rowKey
        onToggleComplete(ex.id, plannedKey, weight, reps)
        awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
        return ex.id
    }

    @Test
    fun plannedRowsAreGeneratedFromTargetSets() = runBlocking {
        val vm = viewModel()
        // Routine target is 3 sets → 3 transient planned rows, none completed.
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.size == 3 }.exercises.first()
        assertEquals(3, ex.rows.size)
        assertTrue(ex.rows.none { it.isCompleted })
    }

    @Test
    fun completingAPlannedRowPersistsItAsGreen() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.completeFirstPlanned("80", "8")

        val row = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }
        assertEquals(BigDecimal("80"), row.weight)
        assertEquals(8, row.repetitions)
        assertEquals(1, database.workoutSetDao().getByExercise(exerciseId).size)
    }

    @Test
    fun addSetAppendsATransientPlannedRowWithoutPersisting() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.size == 3 }.exercises.first()

        vm.onAddSet(ex.id)

        vm.awaitActive { it.exercises.first().rows.size == 4 }
        // Nothing persisted — a planned row is transient.
        assertTrue(database.workoutSetDao().getByExercise(ex.id).isEmpty())
    }

    @Test
    fun deletingAPlannedRowPersistsNothing() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.size == 3 }.exercises.first()
        val plannedKey = ex.rows.first().rowKey

        vm.onDeleteRow(ex.id, plannedKey)

        vm.awaitActive { it.exercises.first().rows.size == 2 }
        assertTrue(database.workoutSetDao().getByExercise(ex.id).isEmpty())
    }

    @Test
    fun deletingACompletedRowTombstonesIt() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.completeFirstPlanned("80", "8")
        val setKey = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }.rowKey

        vm.onDeleteRow(exerciseId, setKey)

        vm.awaitActive { it.exercises.first().rows.none { r -> r.isCompleted } }
        assertTrue(database.workoutSetDao().getByExercise(exerciseId).isEmpty())
    }

    @Test
    fun undoingACompletedRowRevertsToPlannedAndRemovesTheSet() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.completeFirstPlanned("80", "8")
        val setKey = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }.rowKey

        vm.onToggleComplete(exerciseId, setKey, "", "")

        // Set removed (tombstoned) and no completed rows remain.
        vm.awaitActive { it.exercises.first().rows.none { r -> r.isCompleted } }
        assertTrue(database.workoutSetDao().getByExercise(exerciseId).isEmpty())
    }

    @Test
    fun undoingAMiddleSetKeepsItInPlaceRatherThanMovingItLast() = runBlocking {
        val vm = viewModel()
        // Complete all three planned sets top-to-bottom.
        vm.completeFirstPlanned("60", "8")
        vm.awaitActive { it.exercises.first().rows.count { r -> r.isCompleted } == 1 }
        vm.completeFirstPlanned("70", "6")
        vm.awaitActive { it.exercises.first().rows.count { r -> r.isCompleted } == 2 }
        val exerciseId = vm.completeFirstPlanned("80", "4")
        val allDone = vm.awaitActive { it.exercises.first().rows.count { r -> r.isCompleted } == 3 }

        // Undo the middle set (position 2).
        val middle = allDone.exercises.first().rows[1]
        vm.onToggleComplete(exerciseId, middle.rowKey, "", "")

        // Wait for the settled state: 3 rows again (2 completed + the restored planned one),
        // not the transient post-delete state (2 completed rows, before the draft is restored).
        val rows = vm.awaitActive {
            val r = it.exercises.first().rows
            r.size == 3 && r.count { row -> row.isCompleted } == 2
        }.exercises.first().rows
        assertEquals(3, rows.size)
        assertEquals(listOf(1, 2, 3), rows.map { it.setNumber })
        assertTrue(rows[0].isCompleted)
        assertFalse(rows[1].isCompleted) // reverted set stays in position 2, not last
        assertTrue(rows[2].isCompleted)
        // Its typed values survive the undo (revert-to-planned, not discard).
        assertEquals(BigDecimal("70"), rows[1].weight)
    }

    @Test
    fun settingExerciseRestPersistsTheNewDuration() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.isNotEmpty() }.exercises.first()
        assertEquals(90, ex.restSeconds) // routine default

        vm.onSetExerciseRest(ex.id, 150)

        val updated = vm.awaitActive { it.exercises.firstOrNull()?.restSeconds == 150 }
        assertEquals(150, updated.exercises.first().restSeconds)
    }

    @Test
    fun rpeSelectionCompletesAPlannedRowWithThatRpe() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.any { r -> !r.isCompleted } == true }.exercises.first()
        val plannedKey = ex.rows.first { !it.isCompleted }.rowKey
        // Commit weight/reps first (as tapping the RPE cell does in the UI).
        vm.onCommitRow(ex.id, plannedKey, "80", "8")

        vm.onRpeSelected(ex.id, plannedKey, BigDecimal("8.5"))

        val row = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }
        assertEquals(BigDecimal("8.5"), row.rpe)
        assertEquals(BigDecimal("80"), row.weight)
        assertEquals(1, database.workoutSetDao().getByExercise(ex.id).size)
    }

    @Test
    fun recompletingAnUndoneRowViaTheCheckboxKeepsItsRpe() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.any { r -> !r.isCompleted } == true }.exercises.first()
        val plannedKey = ex.rows.first { !it.isCompleted }.rowKey
        // Complete the set with an RPE (fill values, then pick RPE as the UI does).
        vm.onCommitRow(ex.id, plannedKey, "80", "8")
        vm.onRpeSelected(ex.id, plannedKey, BigDecimal("8.5"))
        val completedKey = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }.rowKey

        // Uncheck (undo) → reverts to a planned row that keeps the RPE in memory.
        vm.onToggleComplete(ex.id, completedKey, "", "")
        val reverted = vm.awaitActive { it.exercises.first().rows.none { r -> r.isCompleted } }
            .exercises.first().rows.first()
        assertEquals(BigDecimal("8.5"), reverted.rpe)

        // Re-check via the checkbox → the RPE must be persisted, not dropped.
        vm.onToggleComplete(ex.id, reverted.rowKey, "80", "8")

        val recompleted = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }
        assertEquals(BigDecimal("8.5"), recompleted.rpe)
        assertEquals(BigDecimal("8.5"), database.workoutSetDao().getByExercise(ex.id).first().rpe)
    }

    @Test
    fun rpeSelectionOnACompletedRowEditsItsRpe() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.completeFirstPlanned("80", "8")
        val setKey = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }.rowKey

        vm.onRpeSelected(exerciseId, setKey, BigDecimal("9"))

        val row = vm.awaitActive { it.exercises.first().rows.firstOrNull { r -> r.isCompleted }?.rpe != null }
            .exercises.first().rows.first { it.isCompleted }
        assertTrue(row.isCompleted) // still completed (edit-after-completion)
        assertEquals(BigDecimal("9"), row.rpe)
        assertEquals(1, database.workoutSetDao().getByExercise(exerciseId).size)
    }

    @Test
    fun completingViaToggleLeavesRpeNull() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.completeFirstPlanned("80", "8")
        val row = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }
        assertNull(row.rpe)
    }

    @Test
    fun completingAnEmptyPlannedRowPersistsNothing() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.any { r -> !r.isCompleted } == true }.exercises.first()
        val plannedKey = ex.rows.first { !it.isCompleted }.rowKey

        vm.onToggleComplete(ex.id, plannedKey, "", "")

        // Give any (incorrect) write a chance, then assert nothing was persisted.
        assertTrue(database.workoutSetDao().getByExercise(ex.id).isEmpty())
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

        // Attempting to complete a (now-hidden) planned row is swallowed: the repository
        // rejects a write to a terminal session and the VM catches it.
        vm.onToggleComplete(exerciseId, "draft:${UUID.randomUUID()}", "80", "8")

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

    // --- Milestone E: PREVIOUS column (read-only projection) ---

    @Test
    fun noPreviousShownWhenExerciseHasNoCompletedHistory() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.size == 3 }.exercises.first()
        assertTrue(ex.rows.all { it.previous == null })
    }

    @Test
    fun previousReflectsThePriorCompletedWorkout() = runBlocking {
        // Workout 1: complete one squat set at 100 × 5, then finish.
        val vm1 = viewModel()
        vm1.completeFirstPlanned("100", "5")
        vm1.completeWorkout()
        vm1.awaitActive { it.isReadOnly }

        // Workout 2 (new session, same routine): PREVIOUS shows last time's set 1.
        val vm2 = viewModel()
        val row1 = vm2.awaitActive { it.exercises.firstOrNull()?.rows?.firstOrNull()?.previous != null }
            .exercises.first().rows.first()
        assertEquals("100kg × 5", row1.previous)
        // Set 2 had no prior performance → shown as null (rendered "-").
        assertNull(vm2.uiStateActive().exercises.first().rows[1].previous)
    }

    @Test
    fun sameRoutineStrategyShowsThePriorWorkoutOfThisRoutine() = runBlocking {
        // Workout 1 in this routine: squat 90 × 6, finish.
        val vm1 = viewModel(previousStrategy = PreviousWorkoutValues.SAME_ROUTINE)
        vm1.completeFirstPlanned("90", "6")
        vm1.completeWorkout()
        vm1.awaitActive { it.isReadOnly }

        // Workout 2, same routine, SAME_ROUTINE strategy: PREVIOUS shows last time's set 1.
        val vm2 = viewModel(previousStrategy = PreviousWorkoutValues.SAME_ROUTINE)
        val row1 = vm2.awaitActive { it.exercises.firstOrNull()?.rows?.firstOrNull()?.previous != null }
            .exercises.first().rows.first()
        assertEquals("90kg × 6", row1.previous)
    }

    private fun WorkoutViewModel.uiStateActive() = uiState.value as WorkoutUiState.Active

    // --- Milestone F: exercise management ---

    @Test
    fun removingAnExerciseRemovesItFromState() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.isNotEmpty() }.exercises.first()

        vm.onRemoveExercise(ex.id)

        assertTrue(vm.awaitActive { it.exercises.isEmpty() }.exercises.isEmpty())
    }

    @Test
    fun movingAnExerciseReordersState() = runBlocking {
        val vm = viewModel()
        val active = vm.awaitActive { it.exercises.isNotEmpty() }
        val first = active.exercises.first()
        // Add a second exercise so there is something to reorder.
        workoutRepository.addExercise(active.sessionId, RoutineTestData.benchId, "Bench Press")
        vm.awaitActive { it.exercises.size == 2 }

        vm.onMoveExerciseDown(first.id)

        val reordered = vm.awaitActive { it.exercises.size == 2 && it.exercises.first().id != first.id }
        assertEquals(first.id, reordered.exercises[1].id) // moved from position 0 to 1
    }

    // --- Milestone D: RestEffect consumption ---

    @Test
    fun completingASetStartsTheRestTimerWithExerciseDuration() = runBlocking {
        val vm = viewModel()
        vm.completeFirstPlanned("80", "8")

        val state = vm.restTimer.value
        assertTrue(state is com.myfitnesslog.feature.workout.domain.RestTimerState.Running)
        // Routine target rest is 90s.
        assertEquals(90, (state as com.myfitnesslog.feature.workout.domain.RestTimerState.Running).totalSeconds)
    }

    @Test
    fun undoingACompletionStopsTheRestTimer() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.completeFirstPlanned("80", "8")
        assertTrue(vm.restTimer.value is com.myfitnesslog.feature.workout.domain.RestTimerState.Running)
        val setKey = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }.rowKey

        vm.onToggleComplete(exerciseId, setKey, "", "")

        assertEquals(com.myfitnesslog.feature.workout.domain.RestTimerState.Idle, vm.restTimer.value)
    }

    @Test
    fun editingACompletedSetDoesNotChangeTheRestTimer() = runBlocking {
        val vm = viewModel()
        val exerciseId = vm.completeFirstPlanned("80", "8")
        val running = vm.restTimer.value
        assertTrue(running is com.myfitnesslog.feature.workout.domain.RestTimerState.Running)
        val setKey = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }.rowKey

        vm.onCommitRow(exerciseId, setKey, "85", "8")

        assertEquals(running, vm.restTimer.value) // unchanged — edit is not a rest event
    }

    @Test
    fun completingViaRpeStartsTheRestTimer() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.any { r -> !r.isCompleted } == true }.exercises.first()
        val plannedKey = ex.rows.first { !it.isCompleted }.rowKey
        vm.onCommitRow(ex.id, plannedKey, "80", "8")

        vm.onRpeSelected(ex.id, plannedKey, BigDecimal("8"))

        // Wait for the timer to start rather than reading it immediately.
        // Committing a row and selecting an RPE both dispatch onto the ViewModel's
        // scope, so asserting on the next line is a race: it passes only when that
        // work happens to finish first. It usually did, which is what made this
        // flaky rather than broken. awaitFirst fails on its own timeout if the
        // timer genuinely never starts, so the assertion still has teeth.
        vm.restTimer.awaitFirst { it is com.myfitnesslog.feature.workout.domain.RestTimerState.Running }

        assertTrue(vm.restTimer.value is com.myfitnesslog.feature.workout.domain.RestTimerState.Running)
    }

    /**
     * TD-015. The regression test for the ordering bug behind the flake.
     *
     * `onCommitRow` writes weight and reps into the ViewModel's `drafts` field;
     * `onRpeSelected` then needs them to decide whether the row can be completed.
     * It used to read them back out of `uiState`, which is a `combine`/`stateIn`
     * projection over Room and `drafts`, so the value it had just written was not
     * reliably visible yet. When it was not, the transition returned `None` with
     * `RestEffect.NONE`: nothing persisted, no timer, and the awaits in the two
     * tests below timed out after five real seconds.
     *
     * This asserts the invariant directly rather than reproducing the race: with
     * **no suspension point between the two calls**, the commit must still be
     * visible to the RPE selection. There is deliberately no `awaitActive` in
     * between, because that is exactly the yield that used to hide the bug.
     */
    @Test
    fun rpeSelectionSeesAWeightCommittedImmediatelyBeforeIt() = runBlocking {
        val vm = viewModel()
        val ex = vm.awaitActive { it.exercises.firstOrNull()?.rows?.any { r -> !r.isCompleted } == true }
            .exercises.first()
        val plannedKey = ex.rows.first { !it.isCompleted }.rowKey

        // Back to back, nothing in between. This is the whole point of the test.
        vm.onCommitRow(ex.id, plannedKey, "80", "8")
        vm.onRpeSelected(ex.id, plannedKey, BigDecimal("8"))

        val row = vm.awaitActive { it.exercises.first().rows.any { r -> r.isCompleted } }
            .exercises.first().rows.first { it.isCompleted }
        assertEquals(BigDecimal("80"), row.weight)
        assertEquals(8, row.repetitions)
        assertEquals(BigDecimal("8"), row.rpe)
        assertEquals(1, database.workoutSetDao().getByExercise(ex.id).size)
    }

    @Test
    fun restTimerSurvivesViewModelRecreation() = runBlocking {
        // The rest countdown is app-scoped, so leaving the workout screen (which
        // destroys the ViewModel) and returning to a fresh ViewModel still shows it.
        val sharedTimer = newRestTimer()
        val vm1 = viewModel(restTimer = sharedTimer)
        vm1.awaitActive { it.exercises.isNotEmpty() }
        vm1.startRest(60)
        assertTrue(vm1.restTimer.value is com.myfitnesslog.feature.workout.domain.RestTimerState.Running)

        // A brand-new ViewModel over the same app-scoped timer (as on navigation return).
        val vm2 = viewModel(startFrom = null, restTimer = sharedTimer)
        assertTrue(vm2.restTimer.value is com.myfitnesslog.feature.workout.domain.RestTimerState.Running)
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

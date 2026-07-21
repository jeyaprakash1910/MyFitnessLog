package com.myfitnesslog.core.sync

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.sync.testing.RecordingSyncTrigger
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.RoutineRepositoryImpl
import com.myfitnesslog.feature.workout.data.WorkoutRepositoryImpl
import com.myfitnesslog.feature.workout.domain.StartWorkoutUseCase
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/**
 * Verifies that every local write asks for a synchronization pass.
 *
 * A missed trigger is invisible — the data is saved correctly and simply syncs
 * later than it should — so each mutating entry point is asserted individually
 * rather than trusting that the pattern was applied uniformly.
 */
@RunWith(RobolectricTestRunner::class)
class SyncTriggerIntegrationTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineRepository: RoutineRepositoryImpl
    private lateinit var workoutRepository: WorkoutRepositoryImpl
    private lateinit var startWorkout: StartWorkoutUseCase

    private val trigger = RecordingSyncTrigger()
    private val clock = Clock.fixed(Instant.parse("2026-07-21T09:00:00Z"), ZoneOffset.UTC)

    private val categoryId = UUID.randomUUID()
    private val exerciseId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        database.exerciseCategoryDao()
            .upsert(ExerciseCategoryEntity(id = categoryId, name = "Chest"))
        database.exerciseDao().upsert(
            ExerciseEntity(id = exerciseId, categoryId = categoryId, name = "Bench Press"),
        )

        val dispatcher = UnconfinedTestDispatcher()
        routineRepository = RoutineRepositoryImpl(
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            ioDispatcher = dispatcher,
            clock = clock,
            syncTrigger = trigger,
        )
        workoutRepository = WorkoutRepositoryImpl(
            sessionDao = database.workoutSessionDao(),
            exerciseDao = database.workoutExerciseDao(),
            setDao = database.workoutSetDao(),
            ioDispatcher = dispatcher,
            clock = clock,
            syncTrigger = trigger,
        )
        startWorkout = StartWorkoutUseCase(
            database = database,
            workoutSessionDao = database.workoutSessionDao(),
            workoutExerciseDao = database.workoutExerciseDao(),
            routineRepository = routineRepository,
            clock = clock,
            ioDispatcher = dispatcher,
            syncTrigger = trigger,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** Runs [block], returning how many sync requests it produced. */
    private suspend fun requestsFrom(block: suspend () -> Unit): Int {
        trigger.reset()
        block()
        return trigger.requestCount
    }

    // ---- Routine mutations ------------------------------------------------

    @Test
    fun `every routine mutation requests a sync`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")
        assertEquals(1, requestsFrom { routineRepository.renameRoutine(routineId, "Pull Day") })
        assertEquals(1, requestsFrom { routineRepository.duplicateRoutine(routineId) })
        assertEquals(1, requestsFrom { routineRepository.deleteRoutine(routineId) })
    }

    @Test
    fun `creating a routine requests a sync`() = runTest {
        assertEquals(1, requestsFrom { routineRepository.createRoutine("Push Day") })
    }

    @Test
    fun `every routine-exercise mutation requests a sync`() = runTest {
        val routineId = routineRepository.createRoutine("Push Day")

        var routineExerciseId = UUID.randomUUID()
        assertEquals(
            1,
            requestsFrom {
                routineExerciseId = routineRepository.addExercise(
                    routineId = routineId,
                    exerciseId = exerciseId,
                    targetSets = 3,
                    minTargetReps = 8,
                    maxTargetReps = 12,
                    targetRestSeconds = 90,
                    notes = null,
                )
            },
        )
        assertEquals(
            1,
            requestsFrom {
                routineRepository.updateExercise(
                    routineExerciseId = routineExerciseId,
                    targetSets = 4,
                    minTargetReps = 6,
                    maxTargetReps = 10,
                    targetRestSeconds = 120,
                    notes = "heavier",
                )
            },
        )
        assertEquals(
            1,
            requestsFrom {
                routineRepository.reorderExercises(routineId, listOf(routineExerciseId))
            },
        )
        assertEquals(1, requestsFrom { routineRepository.removeExercise(routineExerciseId) })
    }

    @Test
    fun `a no-op mutation on a missing row requests nothing`() = runTest {
        // These take the early return before writing anything, so there is
        // genuinely nothing to sync.
        assertEquals(0, requestsFrom { routineRepository.renameRoutine(UUID.randomUUID(), "x") })
        assertEquals(0, requestsFrom { routineRepository.deleteRoutine(UUID.randomUUID()) })
        assertEquals(
            0,
            requestsFrom { routineRepository.removeExercise(UUID.randomUUID()) },
        )
    }

    // ---- Workout mutations ------------------------------------------------

    @Test
    fun `starting a workout requests a sync`() = runTest {
        assertEquals(1, requestsFrom { startWorkout(null) })
    }

    @Test
    fun `resuming an existing workout requests nothing`() = runTest {
        startWorkout(null)

        // Nothing was written — the active session was simply returned.
        assertEquals(0, requestsFrom { startWorkout(null) })
    }

    @Test
    fun `every workout mutation requests a sync`() = runTest {
        val sessionId = startWorkout(null)

        var workoutExerciseId = UUID.randomUUID()
        assertEquals(
            1,
            requestsFrom {
                workoutExerciseId =
                    workoutRepository.addExercise(sessionId, exerciseId, "Bench Press")
            },
        )

        var setId = UUID.randomUUID()
        assertEquals(
            1,
            requestsFrom {
                setId = workoutRepository.addSet(
                    workoutExerciseId = workoutExerciseId,
                    weight = BigDecimal("60.00"),
                    repetitions = 10,
                    setCategory = SetCategory.WORKING,
                    rpe = null,
                    rir = null,
                )
            },
        )
        assertEquals(
            1,
            requestsFrom {
                workoutRepository.updateSet(
                    setId = setId,
                    weight = BigDecimal("62.50"),
                    repetitions = 8,
                    setCategory = SetCategory.WORKING,
                    rpe = null,
                    rir = null,
                    isCompleted = true,
                )
            },
        )
        assertEquals(1, requestsFrom { workoutRepository.completeWorkout(sessionId) })
    }

    @Test
    fun `discarding a workout requests a sync`() = runTest {
        val sessionId = startWorkout(null)

        assertEquals(1, requestsFrom { workoutRepository.discardWorkout(sessionId) })
    }

    @Test
    fun `deleting a set requests nothing while deletion propagation is deferred`() = runTest {
        // Documents current, deliberate behaviour rather than endorsing it: a
        // hard-deleted set leaves no record for a pass to upload. Revisit when
        // deletion propagation lands.
        val sessionId = startWorkout(null)
        val workoutExerciseId = workoutRepository.addExercise(sessionId, exerciseId, "Bench Press")
        val setId = workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("60.00"),
            repetitions = 10,
            setCategory = SetCategory.WORKING,
            rpe = null,
            rir = null,
        )

        assertEquals(0, requestsFrom { workoutRepository.deleteSet(setId) })
    }

    @Test
    fun `a realistic session produces a request for every write`() = runTest {
        trigger.reset()

        val routineId = routineRepository.createRoutine("Push Day")
        routineRepository.addExercise(
            routineId = routineId,
            exerciseId = exerciseId,
            targetSets = 3,
            minTargetReps = 8,
            maxTargetReps = 12,
            targetRestSeconds = 90,
            notes = null,
        )
        val sessionId = startWorkout(routineId)
        val workoutExerciseId = workoutRepository.addExercise(sessionId, exerciseId, "Bench Press")
        workoutRepository.addSet(
            workoutExerciseId = workoutExerciseId,
            weight = BigDecimal("60.00"),
            repetitions = 10,
            setCategory = SetCategory.WORKING,
            rpe = null,
            rir = null,
        )
        workoutRepository.completeWorkout(sessionId)

        // create, addExercise, start, addWorkoutExercise, addSet, complete.
        assertEquals(6, trigger.requestCount)
        assertTrue(trigger.requestCount > 0)
    }
}

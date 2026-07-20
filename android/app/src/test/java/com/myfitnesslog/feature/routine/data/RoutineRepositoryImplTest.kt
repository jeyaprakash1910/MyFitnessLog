package com.myfitnesslog.feature.routine.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoutineRepositoryImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var repository: RoutineRepositoryImpl
    private val clock = MutableClock(Instant.ofEpochMilli(1_700_000_000_000L))

    private val categoryId = UUID.randomUUID()
    private val benchId = UUID.randomUUID()
    private val squatId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        database.exerciseCategoryDao().upsert(ExerciseCategoryEntity(categoryId, "Legs"))
        database.exerciseDao().upsertAll(
            listOf(
                ExerciseEntity(benchId, categoryId, "Bench Press"),
                ExerciseEntity(squatId, categoryId, "Squat"),
            ),
        )
        repository = RoutineRepositoryImpl(
            routineDao = database.routineDao(),
            routineExerciseDao = database.routineExerciseDao(),
            ioDispatcher = UnconfinedTestDispatcher(),
            clock = clock,
        )
    }

    @After
    fun tearDown() = database.close()

    private suspend fun addSquat(routineId: UUID) =
        repository.addExercise(routineId, squatId, 5, 5, 5, 120, "heavy")

    @Test
    fun createRoutinePersistsAsPendingAndIsObserved() = runTest {
        val id = repository.createRoutine("Legs")

        val routines = repository.observeRoutines().first()
        assertEquals(1, routines.size)
        assertEquals("Legs", routines.single().name)
        assertEquals(id, routines.single().id)
        assertEquals(SyncStatus.PENDING, routines.single().syncStatus)
    }

    @Test
    fun renameUpdatesNameAndBumpsUpdatedAt() = runTest {
        val id = repository.createRoutine("Legs")
        clock.instant = clock.instant.plusSeconds(60)

        repository.renameRoutine(id, "Leg Day")

        val routine = repository.observeRoutine(id).first()!!
        assertEquals("Leg Day", routine.name)
        assertTrue(routine.updatedAt.isAfter(routine.createdAt))
    }

    @Test
    fun deleteSoftRemovesFromObservedList() = runTest {
        val id = repository.createRoutine("Legs")

        repository.deleteRoutine(id)

        assertTrue(repository.observeRoutines().first().isEmpty())
        assertNull(repository.observeRoutine(id).first())
    }

    @Test
    fun duplicateCopiesRoutineAndExercisesWithNewIds() = runTest {
        val id = repository.createRoutine("Legs")
        addSquat(id)
        repository.addExercise(id, benchId, 3, 8, 12, 90, null)

        val copyId = repository.duplicateRoutine(id)

        assertNotEquals(id, copyId)
        val copy = repository.observeRoutine(copyId).first()!!
        assertEquals("Legs (copy)", copy.name)
        val originalExercises = repository.observeRoutineExercises(id).first()
        val copiedExercises = repository.observeRoutineExercises(copyId).first()
        assertEquals(originalExercises.map { it.exerciseName }, copiedExercises.map { it.exerciseName })
        // Copied rows are new records.
        assertTrue(
            copiedExercises.map { it.routineExercise.id }
                .none { it in originalExercises.map { o -> o.routineExercise.id } },
        )
    }

    @Test
    fun addExerciseAppendsWithIncrementingDisplayOrderAndName() = runTest {
        val id = repository.createRoutine("Legs")
        addSquat(id)
        repository.addExercise(id, benchId, 3, 8, 12, 90, null)

        val details = repository.observeRoutineExercises(id).first()
        assertEquals(listOf("Squat", "Bench Press"), details.map { it.exerciseName })
        assertEquals(listOf(0, 1), details.map { it.routineExercise.displayOrder })
    }

    @Test
    fun updateExerciseChangesTargets() = runTest {
        val id = repository.createRoutine("Legs")
        val reId = addSquat(id)

        repository.updateExercise(reId, targetSets = 4, minTargetReps = 6, maxTargetReps = 10, targetRestSeconds = null, notes = "  ")

        val re = repository.observeRoutineExercises(id).first().single().routineExercise
        assertEquals(4, re.targetSets)
        assertEquals(6, re.minTargetReps)
        assertEquals(10, re.maxTargetReps)
        assertNull(re.targetRestSeconds)
        assertNull(re.notes) // blank normalised to null
    }

    @Test
    fun removeExerciseSoftRemovesFromDetails() = runTest {
        val id = repository.createRoutine("Legs")
        val reId = addSquat(id)

        repository.removeExercise(reId)

        assertTrue(repository.observeRoutineExercises(id).first().isEmpty())
    }

    @Test
    fun reorderExercisesUpdatesDisplayOrder() = runTest {
        val id = repository.createRoutine("Legs")
        val squatRe = addSquat(id)
        val benchRe = repository.addExercise(id, benchId, 3, 8, 12, 90, null)

        repository.reorderExercises(id, listOf(benchRe, squatRe))

        val details = repository.observeRoutineExercises(id).first()
        assertEquals(listOf("Bench Press", "Squat"), details.map { it.exerciseName })
        assertEquals(listOf(0, 1), details.map { it.routineExercise.displayOrder })
    }
}

/** A [Clock] whose instant can be advanced by tests. */
private class MutableClock(var instant: Instant) : Clock() {
    override fun instant(): Instant = instant
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
}

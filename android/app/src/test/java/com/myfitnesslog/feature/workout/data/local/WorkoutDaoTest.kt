package com.myfitnesslog.feature.workout.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkoutDaoTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var sessionDao: WorkoutSessionDao
    private lateinit var exerciseDao: WorkoutExerciseDao
    private lateinit var setDao: WorkoutSetDao

    private val categoryId = UUID.randomUUID()
    private val benchId = UUID.randomUUID()
    private val now = Instant.ofEpochMilli(1_700_000_000_000L)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        sessionDao = database.workoutSessionDao()
        exerciseDao = database.workoutExerciseDao()
        setDao = database.workoutSetDao()
        database.exerciseCategoryDao().upsert(ExerciseCategoryEntity(categoryId, "Legs"))
        database.exerciseDao().upsert(ExerciseEntity(benchId, categoryId, "Bench Press"))
    }

    @After
    fun tearDown() = database.close()

    private fun session(
        id: UUID = UUID.randomUUID(),
        status: WorkoutStatus = WorkoutStatus.IN_PROGRESS,
        startedAt: Instant = now,
    ) = WorkoutSessionEntity(
        id = id, routineId = null, status = status, startedAt = startedAt,
        createdAt = now, updatedAt = now,
    )

    private fun workoutExercise(sessionId: UUID, order: Int, exerciseId: UUID = benchId) =
        WorkoutExerciseEntity(
            id = UUID.randomUUID(), workoutSessionId = sessionId, exerciseId = exerciseId,
            exerciseName = "Bench Press", exerciseOrder = order,
            targetSets = 3, minTargetReps = 8, maxTargetReps = 12, targetRestSeconds = 90,
            createdAt = now, updatedAt = now,
        )

    private fun set(
        workoutExerciseId: UUID,
        number: Int,
        weight: BigDecimal = BigDecimal("80.00"),
        category: SetCategory = SetCategory.WORKING,
        rpe: BigDecimal? = null,
    ) = WorkoutSetEntity(
        id = UUID.randomUUID(), workoutExerciseId = workoutExerciseId, setNumber = number,
        weight = weight, repetitions = 8, setCategory = category, rpe = rpe,
        createdAt = now, updatedAt = now,
    )

    @Test
    fun observeActiveReturnsInProgressOnly() = runTest {
        sessionDao.upsert(session(status = WorkoutStatus.COMPLETED))
        assertNull(sessionDao.observeActive().first())

        val active = session(status = WorkoutStatus.IN_PROGRESS)
        sessionDao.upsert(active)
        assertEquals(active.id, sessionDao.observeActive().first()?.id)
    }

    @Test
    fun workoutExerciseRejectsUnknownSession() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { exerciseDao.upsert(workoutExercise(UUID.randomUUID(), 0)) }
        }
    }

    @Test
    fun workoutExerciseRejectsUnknownExercise() = runTest {
        val s = session()
        sessionDao.upsert(s)
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { exerciseDao.upsert(workoutExercise(s.id, 0, exerciseId = UUID.randomUUID())) }
        }
    }

    @Test
    fun workoutSetRejectsUnknownWorkoutExercise() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { setDao.upsert(set(UUID.randomUUID(), 1)) }
        }
    }

    @Test
    fun deletingSessionCascadesToExercisesAndSets() = runTest {
        val s = session()
        sessionDao.upsert(s)
        val we = workoutExercise(s.id, 0)
        exerciseDao.upsert(we)
        setDao.upsertAll(listOf(set(we.id, 1), set(we.id, 2)))

        sessionDao.deleteById(s.id)

        assertTrue(exerciseDao.getBySession(s.id).isEmpty())
        assertTrue(setDao.getByExercise(we.id).isEmpty())
    }

    @Test
    fun observesExercisesOrderedByExerciseOrder() = runTest {
        val s = session()
        sessionDao.upsert(s)
        exerciseDao.upsert(workoutExercise(s.id, order = 1))
        exerciseDao.upsert(workoutExercise(s.id, order = 0))

        val orders = exerciseDao.observeBySession(s.id).first().map { it.exerciseOrder }
        assertEquals(listOf(0, 1), orders)
    }

    @Test
    fun observesSetsOrderedBySetNumber() = runTest {
        val s = session()
        sessionDao.upsert(s)
        val we = workoutExercise(s.id, 0)
        exerciseDao.upsert(we)
        setDao.upsert(set(we.id, 3))
        setDao.upsert(set(we.id, 1))
        setDao.upsert(set(we.id, 2))

        val numbers = setDao.observeByExercise(we.id).first().map { it.setNumber }
        assertEquals(listOf(1, 2, 3), numbers)
    }

    @Test
    fun roundTripsEnumsAndBigDecimals() = runTest {
        val s = session(status = WorkoutStatus.COMPLETED)
        sessionDao.upsert(s)
        assertEquals(WorkoutStatus.COMPLETED, sessionDao.getById(s.id)?.status)

        // Re-insert as in-progress so the FK chain is valid for the set.
        val active = session()
        sessionDao.upsert(active)
        val we = workoutExercise(active.id, 0)
        exerciseDao.upsert(we)
        val stored = set(we.id, 1, weight = BigDecimal("82.50"), category = SetCategory.TOP_SET, rpe = BigDecimal("8.5"))
        setDao.upsert(stored)

        val read = setDao.getById(stored.id)!!
        assertEquals(SetCategory.TOP_SET, read.setCategory)
        assertEquals(BigDecimal("82.50"), read.weight)
        assertEquals(BigDecimal("8.5"), read.rpe)
    }
}

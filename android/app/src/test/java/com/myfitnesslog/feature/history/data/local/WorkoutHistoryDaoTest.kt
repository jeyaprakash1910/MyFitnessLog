package com.myfitnesslog.feature.history.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSetEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class WorkoutHistoryDaoTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var historyDao: WorkoutHistoryDao

    private val categoryId = UUID.randomUUID()
    private val benchId = UUID.randomUUID()
    private val now = Instant.ofEpochMilli(1_700_000_000_000L)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        historyDao = database.workoutHistoryDao()
        database.exerciseCategoryDao().upsert(ExerciseCategoryEntity(categoryId, "Legs"))
        database.exerciseDao().upsert(ExerciseEntity(benchId, categoryId, "Bench Press"))
    }

    @After
    fun tearDown() = database.close()

    private fun session(
        id: UUID = UUID.randomUUID(),
        status: WorkoutStatus = WorkoutStatus.COMPLETED,
        startedAt: Instant = now,
    ) = WorkoutSessionEntity(
        id = id, routineId = null, status = status, startedAt = startedAt,
        endedAt = startedAt.plusSeconds(3600), createdAt = now, updatedAt = now,
    )

    private suspend fun insertSession(session: WorkoutSessionEntity) =
        database.workoutSessionDao().upsert(session)

    private suspend fun insertExercise(sessionId: UUID, order: Int): UUID {
        val id = UUID.randomUUID()
        database.workoutExerciseDao().upsert(
            WorkoutExerciseEntity(
                id = id, workoutSessionId = sessionId, exerciseId = benchId,
                exerciseName = "Bench Press", exerciseOrder = order,
                targetSets = 3, minTargetReps = 8, maxTargetReps = 12, targetRestSeconds = 90,
                createdAt = now, updatedAt = now,
            ),
        )
        return id
    }

    private suspend fun insertSet(workoutExerciseId: UUID, number: Int) =
        database.workoutSetDao().upsert(
            WorkoutSetEntity(
                id = UUID.randomUUID(), workoutExerciseId = workoutExerciseId, setNumber = number,
                weight = BigDecimal("80.00"), repetitions = 8, setCategory = SetCategory.WORKING,
                createdAt = now, updatedAt = now,
            ),
        )

    @Test
    fun observeCompletedSessionsExcludesInProgressAndDiscarded() = runTest {
        insertSession(session(status = WorkoutStatus.COMPLETED))
        insertSession(session(status = WorkoutStatus.IN_PROGRESS))
        insertSession(session(status = WorkoutStatus.DISCARDED))

        val statuses = historyDao.observeCompletedSessions().first().map { it.status }
        assertEquals(listOf(WorkoutStatus.COMPLETED), statuses)
    }

    @Test
    fun observeCompletedSessionsOrdersNewestFirst() = runTest {
        val older = session(startedAt = now)
        val newer = session(startedAt = now.plusSeconds(86_400))
        insertSession(older)
        insertSession(newer)

        val ids = historyDao.observeCompletedSessions().first().map { it.id }
        assertEquals(listOf(newer.id, older.id), ids)
    }

    @Test
    fun observeCompletedSessionByIdReturnsOnlyCompleted() = runTest {
        val completed = session(status = WorkoutStatus.COMPLETED)
        val discarded = session(status = WorkoutStatus.DISCARDED)
        insertSession(completed)
        insertSession(discarded)

        assertEquals(completed.id, historyDao.observeCompletedSessionById(completed.id).first()?.id)
        assertNull(historyDao.observeCompletedSessionById(discarded.id).first())
    }

    @Test
    fun observeCompletedSummariesReturnsExerciseCountNewestFirst() = runTest {
        val older = session(startedAt = now)
        val newer = session(startedAt = now.plusSeconds(86_400))
        insertSession(older)
        insertSession(newer)
        insertExercise(older.id, order = 0)
        insertExercise(older.id, order = 1)
        // `newer` has no exercises → count 0 (LEFT JOIN keeps it).
        insertSession(session(status = WorkoutStatus.DISCARDED)) // excluded

        val summaries = historyDao.observeCompletedSummaries().first()
        assertEquals(listOf(newer.id, older.id), summaries.map { it.session.id })
        assertEquals(listOf(0, 2), summaries.map { it.exerciseCount })
    }

    @Test
    fun observeExercisesForSessionOrdersByExerciseOrder() = runTest {
        val s = session()
        insertSession(s)
        insertExercise(s.id, order = 1)
        insertExercise(s.id, order = 0)

        val orders = historyDao.observeExercisesForSession(s.id).first().map { it.exerciseOrder }
        assertEquals(listOf(0, 1), orders)
    }

    @Test
    fun observeSetsForSessionGroupsByExerciseThenSetNumber() = runTest {
        val s = session()
        insertSession(s)
        val first = insertExercise(s.id, order = 0)
        val second = insertExercise(s.id, order = 1)
        // Insert out of order to prove the ORDER BY, not insertion order.
        insertSet(second, number = 1)
        insertSet(first, number = 2)
        insertSet(first, number = 1)

        val sets = historyDao.observeSetsForSession(s.id).first()
        assertEquals(
            listOf(first to 1, first to 2, second to 1),
            sets.map { it.workoutExerciseId to it.setNumber },
        )
    }
}

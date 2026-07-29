package com.myfitnesslog.feature.history.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SetCategory
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.routine.data.local.RoutineEntity
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
    private val routineA = UUID.randomUUID()
    private val routineB = UUID.randomUUID()
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
        database.routineDao().upsert(RoutineEntity(routineA, "Routine A", now, now))
        database.routineDao().upsert(RoutineEntity(routineB, "Routine B", now, now))
    }

    @After
    fun tearDown() = database.close()

    private fun session(
        id: UUID = UUID.randomUUID(),
        status: WorkoutStatus = WorkoutStatus.COMPLETED,
        startedAt: Instant = now,
        routineId: UUID? = null,
    ) = WorkoutSessionEntity(
        id = id, routineId = routineId, status = status, startedAt = startedAt,
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

    private suspend fun insertSet(workoutExerciseId: UUID, number: Int, weight: String, reps: Int, rpe: String? = null) =
        database.workoutSetDao().upsert(
            WorkoutSetEntity(
                id = UUID.randomUUID(), workoutExerciseId = workoutExerciseId, setNumber = number,
                weight = BigDecimal(weight), repetitions = reps, rpe = rpe?.let(::BigDecimal),
                setCategory = SetCategory.WORKING, createdAt = now, updatedAt = now,
            ),
        )

    // --- getPreviousSets (V2 Milestone E) -----------------------------------

    @Test
    fun getPreviousSetsReturnsSetsOfTheExerciseFromACompletedWorkout() = runTest {
        val s = session(status = WorkoutStatus.COMPLETED)
        insertSession(s)
        val ex = insertExercise(s.id, order = 0)
        insertSet(ex, 1, "40", 10, "7")
        insertSet(ex, 2, "42.5", 8, "9.5")

        val previous = historyDao.getPreviousSets(benchId)
        assertEquals(listOf(1, 2), previous.map { it.setNumber })
        assertEquals(BigDecimal("40"), previous[0].weight)
        assertEquals(10, previous[0].repetitions)
        assertEquals(BigDecimal("7"), previous[0].rpe)
        assertEquals(BigDecimal("42.5"), previous[1].weight)
    }

    @Test
    fun getPreviousSetsUsesTheMostRecentCompletedWorkout() = runTest {
        val older = session(status = WorkoutStatus.COMPLETED, startedAt = now)
        val newer = session(status = WorkoutStatus.COMPLETED, startedAt = now.plusSeconds(86_400))
        insertSession(older)
        insertSession(newer)
        insertSet(insertExercise(older.id, 0), 1, "60", 5)
        insertSet(insertExercise(newer.id, 0), 1, "80", 8)

        val previous = historyDao.getPreviousSets(benchId)
        // Must come from the NEWER workout, not the older one.
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("80"), previous[0].weight)
    }

    @Test
    fun getPreviousSetsIgnoresInProgressAndDiscardedWorkouts() = runTest {
        val discarded = session(status = WorkoutStatus.DISCARDED, startedAt = now.plusSeconds(200_000))
        val inProgress = session(status = WorkoutStatus.IN_PROGRESS, startedAt = now.plusSeconds(100_000))
        val completed = session(status = WorkoutStatus.COMPLETED, startedAt = now)
        insertSession(discarded); insertSession(inProgress); insertSession(completed)
        insertSet(insertExercise(discarded.id, 0), 1, "999", 1)
        insertSet(insertExercise(inProgress.id, 0), 1, "888", 1)
        insertSet(insertExercise(completed.id, 0), 1, "70", 6)

        val previous = historyDao.getPreviousSets(benchId)
        // Only the completed workout counts, even though the others are newer.
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("70"), previous[0].weight)
    }

    @Test
    fun getPreviousSetsSkipsALaterCompletedWorkoutWhereTheExerciseWasNotLogged() = runTest {
        // The exercise was performed in an older workout, then merely present (as a
        // routine snapshot row) but NOT logged in a newer completed workout.
        val older = session(status = WorkoutStatus.COMPLETED, startedAt = now)
        val newer = session(status = WorkoutStatus.COMPLETED, startedAt = now.plusSeconds(86_400))
        insertSession(older)
        insertSession(newer)
        insertSet(insertExercise(older.id, 0), 1, "47", 9)
        insertExercise(newer.id, 0) // present in the newer workout but no sets logged

        val previous = historyDao.getPreviousSets(benchId)
        // Must fall back to the older workout where it was actually performed, not
        // resolve to the newer (skipped) session and return empty.
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("47"), previous[0].weight)
        assertEquals(9, previous[0].repetitions)
    }

    @Test
    fun getPreviousSetsIsEmptyWhenNoCompletedHistory() = runTest {
        assertEquals(emptyList<PreviousSetPerformance>(), historyDao.getPreviousSets(benchId))
    }

    // --- getPreviousSetsInRoutine (SAME_ROUTINE strategy) --------------------

    @Test
    fun getPreviousSetsInRoutineReturnsTheLatestWorkoutOfThatRoutine() = runTest {
        // Same exercise done more recently in routine B, but the query is scoped to
        // routine A, so it must return routine A's (older) numbers.
        val inRoutineA = session(status = WorkoutStatus.COMPLETED, startedAt = now, routineId = routineA)
        val inRoutineB = session(status = WorkoutStatus.COMPLETED, startedAt = now.plusSeconds(86_400), routineId = routineB)
        insertSession(inRoutineA)
        insertSession(inRoutineB)
        insertSet(insertExercise(inRoutineA.id, 0), 1, "100", 5)
        insertSet(insertExercise(inRoutineB.id, 0), 1, "80", 12)

        val previous = historyDao.getPreviousSetsInRoutine(benchId, routineA)
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("100"), previous[0].weight)
        assertEquals(5, previous[0].repetitions)
    }

    @Test
    fun getPreviousSetsInRoutineUsesTheMostRecentWorkoutWithinTheRoutine() = runTest {
        val older = session(status = WorkoutStatus.COMPLETED, startedAt = now, routineId = routineA)
        val newer = session(status = WorkoutStatus.COMPLETED, startedAt = now.plusSeconds(86_400), routineId = routineA)
        insertSession(older)
        insertSession(newer)
        insertSet(insertExercise(older.id, 0), 1, "60", 5)
        insertSet(insertExercise(newer.id, 0), 1, "85", 7)

        val previous = historyDao.getPreviousSetsInRoutine(benchId, routineA)
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("85"), previous[0].weight)
    }

    @Test
    fun getPreviousSetsInRoutineIgnoresInProgressAndDiscarded() = runTest {
        val discarded = session(status = WorkoutStatus.DISCARDED, startedAt = now.plusSeconds(200_000), routineId = routineA)
        val inProgress = session(status = WorkoutStatus.IN_PROGRESS, startedAt = now.plusSeconds(100_000), routineId = routineA)
        val completed = session(status = WorkoutStatus.COMPLETED, startedAt = now, routineId = routineA)
        insertSession(discarded); insertSession(inProgress); insertSession(completed)
        insertSet(insertExercise(discarded.id, 0), 1, "999", 1)
        insertSet(insertExercise(inProgress.id, 0), 1, "888", 1)
        insertSet(insertExercise(completed.id, 0), 1, "70", 6)

        val previous = historyDao.getPreviousSetsInRoutine(benchId, routineA)
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("70"), previous[0].weight)
    }

    @Test
    fun getPreviousSetsInRoutineSkipsAWorkoutWhereTheExerciseWasNotLogged() = runTest {
        val older = session(status = WorkoutStatus.COMPLETED, startedAt = now, routineId = routineA)
        val newer = session(status = WorkoutStatus.COMPLETED, startedAt = now.plusSeconds(86_400), routineId = routineA)
        insertSession(older)
        insertSession(newer)
        insertSet(insertExercise(older.id, 0), 1, "47", 9)
        insertExercise(newer.id, 0) // present but no sets logged

        val previous = historyDao.getPreviousSetsInRoutine(benchId, routineA)
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("47"), previous[0].weight)
    }

    @Test
    fun getPreviousSetsInRoutineIsEmptyWhenExerciseNeverDoneInThatRoutine() = runTest {
        val inRoutineB = session(status = WorkoutStatus.COMPLETED, startedAt = now, routineId = routineB)
        insertSession(inRoutineB)
        insertSet(insertExercise(inRoutineB.id, 0), 1, "80", 8)

        // The exercise exists in routine B's history, but not routine A's.
        assertEquals(emptyList<PreviousSetPerformance>(), historyDao.getPreviousSetsInRoutine(benchId, routineA))
    }

    @Test
    fun getPreviousSetsInRoutineIsEmptyForAManualWorkout() = runTest {
        val manual = session(status = WorkoutStatus.COMPLETED, startedAt = now, routineId = null)
        insertSession(manual)
        insertSet(insertExercise(manual.id, 0), 1, "80", 8)

        // A null routineId has no "same routine": passing null must match nothing.
        assertEquals(emptyList<PreviousSetPerformance>(), historyDao.getPreviousSetsInRoutine(benchId, null))
    }

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

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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

/**
 * Model-based / property test for the two "Previous Workout Values" strategies.
 *
 * Strategy: generate many randomized workout datasets (varied routines, exercises,
 * statuses, logged vs. skipped exercises, ordering) and check the actual Room
 * queries — [WorkoutHistoryDao.getPreviousSets] (ANY_WORKOUT) and
 * [WorkoutHistoryDao.getPreviousSetsInRoutine] (SAME_ROUTINE) — against an
 * independent Kotlin oracle that re-derives the spec semantics from the same data.
 * Divergence between SQL and oracle => bug.
 *
 * Plus explicit, human-readable edge scenarios (spec examples, ties, duplicate
 * exercise rows, manual workouts) that double as documentation.
 */
@RunWith(RobolectricTestRunner::class)
class PreviousPerformanceScenarioTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var dao: WorkoutHistoryDao

    private val now = Instant.ofEpochMilli(1_700_000_000_000L)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.workoutHistoryDao()
    }

    @After
    fun tearDown() = database.close()

    // --- In-memory model mirroring what we insert (the oracle's source of truth) -

    private data class SetModel(val setNumber: Int, val weight: BigDecimal, val reps: Int, val rpe: BigDecimal?)
    private data class ExerciseRowModel(val exerciseId: UUID, val sets: List<SetModel>)
    private data class SessionModel(
        val id: UUID,
        val routineId: UUID?,
        val status: WorkoutStatus,
        val startedAt: Instant,
        val rows: List<ExerciseRowModel>,
    )

    private data class Dataset(
        val sessions: List<SessionModel>,
        val exerciseIds: List<UUID>,
        val routineIds: List<UUID>,
    )

    // Oracle: latest COMPLETED session containing exercise with >=1 logged set.
    private fun oracleAny(ds: Dataset, exerciseId: UUID): List<SetModel> =
        ds.sessions
            .filter { it.status == WorkoutStatus.COMPLETED }
            .filter { s -> s.rows.any { it.exerciseId == exerciseId && it.sets.isNotEmpty() } }
            .maxByOrNull { it.startedAt }
            ?.let { s -> s.rows.filter { it.exerciseId == exerciseId }.flatMap { it.sets } }
            ?.sortedBy { it.setNumber }
            .orEmpty()

    // Oracle: same but restricted to routineId; null routine matches nothing.
    private fun oracleSameRoutine(ds: Dataset, exerciseId: UUID, routineId: UUID?): List<SetModel> {
        if (routineId == null) return emptyList()
        return ds.sessions
            .filter { it.status == WorkoutStatus.COMPLETED && it.routineId == routineId }
            .filter { s -> s.rows.any { it.exerciseId == exerciseId && it.sets.isNotEmpty() } }
            .maxByOrNull { it.startedAt }
            ?.let { s -> s.rows.filter { it.exerciseId == exerciseId }.flatMap { it.sets } }
            ?.sortedBy { it.setNumber }
            .orEmpty()
    }

    // --- Insertion --------------------------------------------------------------

    private fun insert(ds: Dataset) = runBlocking {
        database.exerciseCategoryDao().upsert(ExerciseCategoryEntity(CATEGORY_ID, "Cat"))
        ds.exerciseIds.forEach { database.exerciseDao().upsert(ExerciseEntity(it, CATEGORY_ID, "Ex-$it")) }
        ds.routineIds.forEach { database.routineDao().upsert(RoutineEntity(it, "R-$it", now, now)) }
        ds.sessions.forEach { s ->
            database.workoutSessionDao().upsert(
                WorkoutSessionEntity(
                    id = s.id, routineId = s.routineId, status = s.status, startedAt = s.startedAt,
                    endedAt = s.startedAt.plusSeconds(3600), createdAt = now, updatedAt = now,
                ),
            )
            s.rows.forEachIndexed { order, row ->
                val exRowId = UUID.randomUUID()
                database.workoutExerciseDao().upsert(
                    WorkoutExerciseEntity(
                        id = exRowId, workoutSessionId = s.id, exerciseId = row.exerciseId,
                        exerciseName = "Ex-${row.exerciseId}", exerciseOrder = order,
                        targetSets = 3, minTargetReps = 8, maxTargetReps = 12, targetRestSeconds = 90,
                        createdAt = now, updatedAt = now,
                    ),
                )
                row.sets.forEach { set ->
                    database.workoutSetDao().upsert(
                        WorkoutSetEntity(
                            id = UUID.randomUUID(), workoutExerciseId = exRowId, setNumber = set.setNumber,
                            weight = set.weight, repetitions = set.reps, rpe = set.rpe,
                            setCategory = SetCategory.WORKING, createdAt = now, updatedAt = now,
                        ),
                    )
                }
            }
        }
    }

    // --- Random dataset generator (unique startedAt => deterministic "latest") ---

    private fun generate(rnd: Random): Dataset {
        val exerciseIds = List(rnd.nextInt(2, 5)) { UUID.randomUUID() }
        val routineIds = List(rnd.nextInt(1, 4)) { UUID.randomUUID() }
        val statuses = listOf(
            WorkoutStatus.COMPLETED, WorkoutStatus.COMPLETED, WorkoutStatus.COMPLETED,
            WorkoutStatus.IN_PROGRESS, WorkoutStatus.DISCARDED,
        )
        val sessionCount = rnd.nextInt(0, 10)
        val sessions = (0 until sessionCount).map { i ->
            val routineId = if (rnd.nextInt(10) < 3) null else routineIds[rnd.nextInt(routineIds.size)]
            val rows = exerciseIds.mapNotNull { ex ->
                if (rnd.nextInt(10) < 6) {
                    val k = rnd.nextInt(0, 5) // 0 => present but unlogged
                    val sets = (1..k).map { n ->
                        SetModel(
                            setNumber = n,
                            weight = BigDecimal(rnd.nextInt(20, 121)),
                            reps = rnd.nextInt(1, 16),
                            rpe = if (rnd.nextBoolean()) BigDecimal(rnd.nextInt(12, 21)).divide(BigDecimal(2)) else null,
                        )
                    }
                    ExerciseRowModel(ex, sets)
                } else {
                    null
                }
            }
            SessionModel(
                id = UUID.randomUUID(),
                routineId = routineId,
                status = statuses[rnd.nextInt(statuses.size)],
                // Strictly increasing => no ties, so "latest" is unambiguous.
                startedAt = now.plusSeconds(i.toLong() * 3_600),
                rows = rows,
            )
        }
        return Dataset(sessions, exerciseIds, routineIds)
    }

    private fun assertMatch(label: String, expected: List<SetModel>, actual: List<PreviousSetPerformance>) {
        assertEquals("$label: size", expected.size, actual.size)
        expected.forEachIndexed { i, e ->
            val a = actual[i]
            assertEquals("$label[$i]: setNumber", e.setNumber, a.setNumber)
            assertTrue("$label[$i]: weight ${e.weight} vs ${a.weight}", e.weight.compareTo(a.weight) == 0)
            assertEquals("$label[$i]: reps", e.reps, a.repetitions)
            val rpeMatch = (e.rpe == null && a.rpe == null) || (e.rpe != null && a.rpe != null && e.rpe.compareTo(a.rpe) == 0)
            assertTrue("$label[$i]: rpe ${e.rpe} vs ${a.rpe}", rpeMatch)
        }
    }

    @Test
    fun propertyBased_bothStrategiesMatchTheOracleAcrossManyRandomDatasets() {
        val iterations = 300
        var anyComparisons = 0
        var routineComparisons = 0
        var nonEmptyResults = 0
        for (seed in 1..iterations) {
            database.clearAllTables()
            val ds = generate(Random(seed))
            insert(ds)
            for (ex in ds.exerciseIds) {
                val expAny = oracleAny(ds, ex)
                val actAny = runBlocking { dao.getPreviousSets(ex) }
                assertMatch("seed=$seed ANY_WORKOUT ex=$ex", expAny, actAny)
                anyComparisons++
                if (expAny.isNotEmpty()) nonEmptyResults++
                for (r in ds.routineIds + listOf<UUID?>(null)) {
                    val expR = oracleSameRoutine(ds, ex, r)
                    val actR = runBlocking { dao.getPreviousSetsInRoutine(ex, r) }
                    assertMatch("seed=$seed SAME_ROUTINE ex=$ex routine=$r", expR, actR)
                    routineComparisons++
                    if (expR.isNotEmpty()) nonEmptyResults++
                }
            }
        }
        println(
            "SCENARIO_SUMMARY iterations=$iterations anyComparisons=$anyComparisons " +
                "routineComparisons=$routineComparisons totalComparisons=${anyComparisons + routineComparisons} " +
                "nonEmptyResults=$nonEmptyResults mismatches=0",
        )
    }

    // --- Explicit documentation scenarios --------------------------------------

    private fun completedSession(routineId: UUID?, startedAt: Instant): UUID {
        val id = UUID.randomUUID()
        runBlocking {
            database.workoutSessionDao().upsert(
                WorkoutSessionEntity(
                    id = id, routineId = routineId, status = WorkoutStatus.COMPLETED, startedAt = startedAt,
                    endedAt = startedAt.plusSeconds(3600), createdAt = now, updatedAt = now,
                ),
            )
        }
        return id
    }

    private fun addExerciseWithSets(sessionId: UUID, exerciseId: UUID, order: Int, vararg sets: Pair<Int, Pair<String, Int>>) {
        val exRowId = UUID.randomUUID()
        runBlocking {
            database.workoutExerciseDao().upsert(
                WorkoutExerciseEntity(
                    id = exRowId, workoutSessionId = sessionId, exerciseId = exerciseId,
                    exerciseName = "Ex", exerciseOrder = order,
                    targetSets = 3, minTargetReps = 8, maxTargetReps = 12, targetRestSeconds = 90,
                    createdAt = now, updatedAt = now,
                ),
            )
            sets.forEach { (n, wr) ->
                database.workoutSetDao().upsert(
                    WorkoutSetEntity(
                        id = UUID.randomUUID(), workoutExerciseId = exRowId, setNumber = n,
                        weight = BigDecimal(wr.first), repetitions = wr.second, rpe = null,
                        setCategory = SetCategory.WORKING, createdAt = now, updatedAt = now,
                    ),
                )
            }
        }
    }

    private fun seedMaster(exerciseId: UUID, vararg routineIds: UUID) = runBlocking {
        database.exerciseCategoryDao().upsert(ExerciseCategoryEntity(CATEGORY_ID, "Cat"))
        database.exerciseDao().upsert(ExerciseEntity(exerciseId, CATEGORY_ID, "Bench"))
        routineIds.forEach { database.routineDao().upsert(RoutineEntity(it, "R", now, now)) }
    }

    @Test
    fun example_anyWorkout_picksLatestAcrossRoutines() {
        val bench = UUID.randomUUID()
        val push = UUID.randomUUID()
        val pull = UUID.randomUUID()
        seedMaster(bench, push, pull)
        // Bench 80x8 in Push (older); Bench 85x7 in Pull (newer).
        addExerciseWithSets(completedSession(push, now), bench, 0, 1 to ("80" to 8))
        addExerciseWithSets(completedSession(pull, now.plusSeconds(86_400)), bench, 0, 1 to ("85" to 7))

        val previous = runBlocking { dao.getPreviousSets(bench) }
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("85"), previous[0].weight)
        assertEquals(7, previous[0].repetitions)
    }

    @Test
    fun example_sameRoutine_ignoresMoreRecentOtherRoutine() {
        val bench = UUID.randomUUID()
        val pushA = UUID.randomUUID()
        val pushB = UUID.randomUUID()
        seedMaster(bench, pushA, pushB)
        // Push A: Bench 100x5 (older). Push B: Bench 80x12 (newer).
        addExerciseWithSets(completedSession(pushA, now), bench, 0, 1 to ("100" to 5))
        addExerciseWithSets(completedSession(pushB, now.plusSeconds(86_400)), bench, 0, 1 to ("80" to 12))

        // Querying Push A must return 100x5, NOT the newer 80x12 from Push B.
        val previous = runBlocking { dao.getPreviousSetsInRoutine(bench, pushA) }
        assertEquals(1, previous.size)
        assertEquals(BigDecimal("100"), previous[0].weight)
        assertEquals(5, previous[0].repetitions)
    }

    @Test
    fun example_skipsLatestSessionWhereExerciseWasPresentButUnlogged() {
        val bench = UUID.randomUUID()
        val routine = UUID.randomUUID()
        seedMaster(bench, routine)
        addExerciseWithSets(completedSession(routine, now), bench, 0, 1 to ("47" to 9)) // logged (older)
        // Newer session lists bench but logs no sets:
        val newer = completedSession(routine, now.plusSeconds(86_400))
        addExerciseWithSets(newer, bench, 0) // no sets

        assertEquals(BigDecimal("47"), runBlocking { dao.getPreviousSets(bench) }[0].weight)
        assertEquals(BigDecimal("47"), runBlocking { dao.getPreviousSetsInRoutine(bench, routine) }[0].weight)
    }

    @Test
    fun edge_tie_sameStartedAt_returnsOneOfTheCandidates_deterministicChoiceNotGuaranteed() {
        val bench = UUID.randomUUID()
        val routine = UUID.randomUUID()
        seedMaster(bench, routine)
        // Two COMPLETED sessions with the SAME startedAt — ORDER BY ... LIMIT 1 tie.
        addExerciseWithSets(completedSession(routine, now), bench, 0, 1 to ("60" to 5))
        addExerciseWithSets(completedSession(routine, now), bench, 0, 1 to ("70" to 5))

        val previous = runBlocking { dao.getPreviousSets(bench) }
        assertEquals(1, previous.size)
        // Behaviour: returns one of the two; SQLite does not guarantee which on a tie.
        assertTrue(previous[0].weight.compareTo(BigDecimal("60")) == 0 || previous[0].weight.compareTo(BigDecimal("70")) == 0)
    }

    @Test
    fun edge_sameExerciseLoggedTwiceInOneSession_returnsAllRowsSets() {
        val bench = UUID.randomUUID()
        val routine = UUID.randomUUID()
        seedMaster(bench, routine)
        val session = completedSession(routine, now)
        // The same exercise added twice in one session (two workout_exercise rows).
        addExerciseWithSets(session, bench, 0, 1 to ("60" to 5))
        addExerciseWithSets(session, bench, 1, 1 to ("65" to 5))

        val previous = runBlocking { dao.getPreviousSets(bench) }
        // Behaviour: both rows' sets are returned (2 rows, both setNumber=1).
        assertEquals(2, previous.size)
        assertTrue(previous.all { it.setNumber == 1 })
    }

    @Test
    fun edge_manualWorkout_sameRoutineReturnsEmpty() {
        val bench = UUID.randomUUID()
        seedMaster(bench)
        // A completed MANUAL workout (null routineId) that logged the exercise.
        addExerciseWithSets(completedSession(null, now), bench, 0, 1 to ("80" to 8))

        // ANY_WORKOUT sees it; SAME_ROUTINE with null has no routine => empty.
        assertEquals(1, runBlocking { dao.getPreviousSets(bench) }.size)
        assertEquals(0, runBlocking { dao.getPreviousSetsInRoutine(bench, null) }.size)
    }

    private companion object {
        val CATEGORY_ID: UUID = UUID.randomUUID()
    }
}

package com.myfitnesslog.feature.routine.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.core.data.local.SyncStatus
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class RoutineDaoTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var routineDao: RoutineDao
    private lateinit var routineExerciseDao: RoutineExerciseDao

    private val categoryId = UUID.randomUUID()
    private val benchId = UUID.randomUUID()
    private val squatId = UUID.randomUUID()
    private val now = Instant.ofEpochMilli(1_700_000_000_000L)

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        routineDao = database.routineDao()
        routineExerciseDao = database.routineExerciseDao()
        // Seed master data so routine-exercise FKs are satisfied.
        database.exerciseCategoryDao().upsert(ExerciseCategoryEntity(categoryId, "Legs"))
        database.exerciseDao().upsertAll(
            listOf(
                ExerciseEntity(benchId, categoryId, "Bench Press"),
                ExerciseEntity(squatId, categoryId, "Squat"),
            ),
        )
    }

    @After
    fun tearDown() = database.close()

    private fun routine(name: String, id: UUID = UUID.randomUUID(), deleted: Boolean = false) =
        RoutineEntity(id, name, now, now, deleted, SyncStatus.PENDING)

    private fun routineExercise(routineId: UUID, exerciseId: UUID, order: Int) =
        RoutineExerciseEntity(
            id = UUID.randomUUID(),
            routineId = routineId,
            exerciseId = exerciseId,
            displayOrder = order,
            targetSets = 3,
            minTargetReps = 8,
            maxTargetReps = 12,
            targetRestSeconds = 90,
            notes = null,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun observeAllExcludesDeletedAndOrdersByName() = runTest {
        routineDao.upsert(routine("Push"))
        routineDao.upsert(routine("Legs"))
        routineDao.upsert(routine("Archived", deleted = true))

        assertEquals(listOf("Legs", "Push"), routineDao.observeAll().first().map { it.name })
    }

    @Test
    fun observeByIdReturnsOnlyNonDeleted() = runTest {
        val deleted = routine("Gone", deleted = true)
        routineDao.upsert(deleted)

        assertEquals(null, routineDao.observeById(deleted.id).first())
    }

    @Test
    fun observeDetailsJoinsExerciseNameOrderedByDisplayOrder() = runTest {
        val r = routine("Legs")
        routineDao.upsert(r)
        routineExerciseDao.upsert(routineExercise(r.id, squatId, order = 1))
        routineExerciseDao.upsert(routineExercise(r.id, benchId, order = 0))

        val details = routineExerciseDao.observeDetailsByRoutine(r.id).first()
        assertEquals(listOf("Bench Press", "Squat"), details.map { it.exerciseName })
    }

    @Test
    fun observeDetailsExcludesSoftDeletedExercises() = runTest {
        val r = routine("Legs")
        routineDao.upsert(r)
        val re = routineExercise(r.id, squatId, order = 0)
        routineExerciseDao.upsert(re)
        routineExerciseDao.upsert(re.copy(isDeleted = true))

        assertEquals(emptyList<String>(), routineExerciseDao.observeDetailsByRoutine(r.id).first().map { it.exerciseName })
    }

    @Test
    fun routineExerciseRejectsUnknownRoutineForeignKey() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { routineExerciseDao.upsert(routineExercise(UUID.randomUUID(), benchId, 0)) }
        }
    }

    @Test
    fun routineExerciseRejectsUnknownExerciseForeignKey() = runTest {
        val r = routine("Legs")
        routineDao.upsert(r)
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { routineExerciseDao.upsert(routineExercise(r.id, UUID.randomUUID(), 0)) }
        }
    }
}

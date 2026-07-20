package com.myfitnesslog.feature.exercise.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * DAO tests exercising a real (in-memory) Room database on the JVM via
 * Robolectric — no emulator required. Covers insert, upsert, reactive reads and
 * foreign-key enforcement for the reference-data layer.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseDaoTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var categoryDao: ExerciseCategoryDao
    private lateinit var exerciseDao: ExerciseDao

    private val chest = ExerciseCategoryEntity(id = UUID.randomUUID(), name = "Chest")
    private val benchPress = ExerciseEntity(
        id = UUID.randomUUID(),
        categoryId = chest.id,
        name = "Bench Press",
        description = "Barbell press on a flat bench.",
        instructions = "Lower to chest, press up.",
        equipment = "Barbell",
    )

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        categoryDao = database.exerciseCategoryDao()
        exerciseDao = database.exerciseDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun shouldInsertAndObserveCategory() = runTest {
        categoryDao.upsert(chest)

        val all = categoryDao.observeAll().first()
        assertEquals(listOf(chest), all)
    }

    @Test
    fun shouldObserveCategoryById() = runTest {
        categoryDao.upsert(chest)

        assertEquals(chest, categoryDao.observeById(chest.id).first())
        assertNull(categoryDao.observeById(UUID.randomUUID()).first())
    }

    @Test
    fun shouldUpsertReplaceExistingCategoryByPrimaryKey() = runTest {
        categoryDao.upsert(chest)
        categoryDao.upsert(chest.copy(name = "Chest & Triceps"))

        val all = categoryDao.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Chest & Triceps", all.single().name)
    }

    @Test
    fun shouldUpsertAllAndObserveOrderedByName() = runTest {
        val back = ExerciseCategoryEntity(id = UUID.randomUUID(), name = "Back")
        val arms = ExerciseCategoryEntity(id = UUID.randomUUID(), name = "Arms")

        categoryDao.upsertAll(listOf(chest, back, arms))

        val names = categoryDao.observeAll().first().map { it.name }
        assertEquals(listOf("Arms", "Back", "Chest"), names)
    }

    @Test
    fun shouldInsertAndObserveExerciseWithinCategory() = runTest {
        categoryDao.upsert(chest)
        exerciseDao.upsert(benchPress)

        assertEquals(listOf(benchPress), exerciseDao.observeAll().first())
        assertEquals(benchPress, exerciseDao.observeById(benchPress.id).first())
    }

    @Test
    fun shouldRejectExerciseWithUnknownCategoryDueToForeignKey() {
        // No category inserted → the foreign key constraint must fail.
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { exerciseDao.upsert(benchPress) }
        }
    }

    // --- observeFiltered: search + category filtering (all local) ---

    private val backId = UUID.randomUUID()
    private val inclineDbPress = ExerciseEntity(
        id = UUID.randomUUID(),
        categoryId = chest.id,
        name = "Incline Dumbbell Press",
        equipment = "Dumbbell",
    )
    private val pullUp = ExerciseEntity(
        id = UUID.randomUUID(),
        categoryId = backId,
        name = "Pull Up",
        equipment = null,
    )

    private suspend fun seedFilterFixture() {
        categoryDao.upsertAll(
            listOf(chest, ExerciseCategoryEntity(id = backId, name = "Back")),
        )
        exerciseDao.upsertAll(listOf(benchPress, inclineDbPress, pullUp))
    }

    @Test
    fun observeFilteredWithNoFiltersReturnsAllOrderedByName() = runTest {
        seedFilterFixture()

        val names = exerciseDao.observeFiltered(categoryId = null, query = null)
            .first().map { it.name }
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press", "Pull Up"), names)
    }

    @Test
    fun observeFilteredByCategoryReturnsOnlyThatCategory() = runTest {
        seedFilterFixture()

        val names = exerciseDao.observeFiltered(categoryId = chest.id, query = null)
            .first().map { it.name }
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press"), names)
    }

    @Test
    fun observeFilteredBySearchIsCaseInsensitiveSubstring() = runTest {
        seedFilterFixture()

        val names = exerciseDao.observeFiltered(categoryId = null, query = "press")
            .first().map { it.name }
        assertEquals(listOf("Bench Press", "Incline Dumbbell Press"), names)
    }

    @Test
    fun observeFilteredByCategoryAndSearchCombinesBothPredicates() = runTest {
        seedFilterFixture()

        val names = exerciseDao.observeFiltered(categoryId = chest.id, query = "incline")
            .first().map { it.name }
        assertEquals(listOf("Incline Dumbbell Press"), names)
    }
}

package com.myfitnesslog.feature.exercise.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.remote.ExerciseApi
import com.myfitnesslog.feature.exercise.data.remote.ExerciseDto
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.util.UUID

/**
 * Repository tests for [ExerciseRepositoryImpl]. A parent category is seeded
 * first because exercises reference it by foreign key.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseRepositoryImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var api: FakeExerciseApi
    private lateinit var repository: ExerciseRepositoryImpl

    private val categoryId = UUID.randomUUID()
    private val benchId = UUID.randomUUID()

    @Before
    fun setUp() = runTest {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        // Seed the parent category so the FK is satisfied.
        database.exerciseCategoryDao()
            .upsert(ExerciseCategoryEntity(id = categoryId, name = "Chest"))

        api = FakeExerciseApi()
        repository = ExerciseRepositoryImpl(
            dao = database.exerciseDao(),
            api = api,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun benchDto(name: String) = ExerciseDto(
        id = benchId.toString(),
        categoryId = categoryId.toString(),
        name = name,
        description = "Barbell press.",
        instructions = "Press up.",
        equipment = "Barbell",
    )

    @Test
    fun refreshInsertsDownloadedExercisesIntoRoom() = runTest {
        api.response = listOf(benchDto("Bench Press"))

        repository.refresh()

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Bench Press", all.single().name)
        assertEquals(categoryId, all.single().categoryId)
    }

    @Test
    fun refreshUpsertsExistingRowByPrimaryKey() = runTest {
        api.response = listOf(benchDto("Bench Press"))
        repository.refresh()

        api.response = listOf(benchDto("Barbell Bench Press"))
        repository.refresh()

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Barbell Bench Press", all.single().name)
    }

    @Test
    fun apiFailureDoesNotCorruptExistingLocalData() = runTest {
        api.response = listOf(benchDto("Bench Press"))
        repository.refresh()

        api.error = IOException("network down")
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.refresh() }
        }

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Bench Press", all.single().name)
    }

    @Test
    fun observeFilteredAppliesCategoryAndSearchLocally() = runTest {
        val backId = UUID.randomUUID()
        database.exerciseCategoryDao()
            .upsert(ExerciseCategoryEntity(id = backId, name = "Back"))
        api.response = listOf(
            benchDto("Bench Press"),
            ExerciseDto(UUID.randomUUID().toString(), categoryId.toString(), "Incline Press"),
            ExerciseDto(UUID.randomUUID().toString(), backId.toString(), "Pull Up"),
        )
        repository.refresh()

        // Category-only filter.
        assertEquals(
            listOf("Bench Press", "Incline Press"),
            repository.observeFiltered(categoryId, null).first().map { it.name },
        )
        // Search-only filter (case-insensitive).
        assertEquals(
            listOf("Bench Press", "Incline Press"),
            repository.observeFiltered(null, "press").first().map { it.name },
        )
        // Combined filter.
        assertEquals(
            listOf("Incline Press"),
            repository.observeFiltered(categoryId, "incline").first().map { it.name },
        )
    }
}

/** Fake [ExerciseApi] driven by test-controlled state. */
private class FakeExerciseApi : ExerciseApi {
    var response: List<ExerciseDto> = emptyList()
    var error: Throwable? = null

    override suspend fun getExercises(): List<ExerciseDto> {
        error?.let { throw it }
        return response
    }
}

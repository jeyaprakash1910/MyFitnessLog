package com.myfitnesslog.feature.exercise.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.remote.ExerciseApi
import com.myfitnesslog.feature.exercise.data.remote.ExerciseCategoryApi
import com.myfitnesslog.feature.exercise.data.remote.ExerciseCategoryDto
import kotlinx.coroutines.runBlocking
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
    private lateinit var categoryApi: StubCategoryApi
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
        categoryApi = StubCategoryApi()
        repository = ExerciseRepositoryImpl(
            dao = database.exerciseDao(),
            api = api,
            categoryRepository = ExerciseCategoryRepositoryImpl(
                dao = database.exerciseCategoryDao(),
                api = categoryApi,
                ioDispatcher = UnconfinedTestDispatcher(),
            ),
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

        repository.refreshLibrary()

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Bench Press", all.single().name)
        assertEquals(categoryId, all.single().categoryId)
    }

    @Test
    fun refreshUpsertsExistingRowByPrimaryKey() = runTest {
        api.response = listOf(benchDto("Bench Press"))
        repository.refreshLibrary()

        api.response = listOf(benchDto("Barbell Bench Press"))
        repository.refreshLibrary()

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Barbell Bench Press", all.single().name)
    }

    @Test
    fun apiFailureDoesNotCorruptExistingLocalData() = runTest {
        api.response = listOf(benchDto("Bench Press"))
        repository.refreshLibrary()

        api.error = IOException("network down")
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.refreshLibrary() }
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
        repository.refreshLibrary()

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

    /**
     * The regression test for the bug that made the app unusable: refreshing
     * exercises alone on a cold database violates ExerciseEntity's RESTRICT
     * foreign key to ExerciseCategoryEntity. refreshLibrary must download
     * categories first.
     *
     * The database here is deliberately wiped of the category seeded in setUp,
     * so this exercises the genuine fresh-install path.
     */
    @Test
    fun `refreshLibrary downloads categories before exercises on a cold database`() = runTest {
        val cold = coldStack()
        assertEquals(
            emptyList<String>(),
            cold.database.exerciseCategoryDao().observeAll().first().map { it.name },
        )
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
        )
        cold.api.response = listOf(benchDto("Bench Press"))

        cold.repository.refreshLibrary()

        assertEquals(
            listOf("Chest"),
            cold.database.exerciseCategoryDao().observeAll().first().map { it.name },
        )
        assertEquals(listOf("Bench Press"), cold.repository.observeAll().first().map { it.name })
        cold.database.close()
    }

    /** A failed category download must not leave a half-populated library. */
    @Test
    fun `refreshLibrary that fails on categories does not write exercises`() = runTest {
        val cold = coldStack()
        cold.categoryApi.error = IOException("offline")
        cold.api.response = listOf(benchDto("Bench Press"))

        assertThrows(IOException::class.java) { runBlocking { cold.repository.refreshLibrary() } }

        assertEquals(emptyList<String>(), cold.repository.observeAll().first().map { it.name })
        cold.database.close()
    }

    private class ColdStack(
        val database: MyFitnessLogDatabase,
        val api: FakeExerciseApi,
        val categoryApi: StubCategoryApi,
        val repository: ExerciseRepositoryImpl,
    )

    /**
     * A completely empty database and repository — the fresh-install state, with
     * no category seeded. [setUp] seeds one so the FK is satisfied, which is
     * exactly the condition that hid this bug.
     */
    private fun coldStack(): ColdStack {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        val exerciseApi = FakeExerciseApi()
        val catApi = StubCategoryApi()
        return ColdStack(
            database = db,
            api = exerciseApi,
            categoryApi = catApi,
            repository = ExerciseRepositoryImpl(
                dao = db.exerciseDao(),
                api = exerciseApi,
                categoryRepository = ExerciseCategoryRepositoryImpl(
                    dao = db.exerciseCategoryDao(),
                    api = catApi,
                    ioDispatcher = UnconfinedTestDispatcher(),
                ),
                ioDispatcher = UnconfinedTestDispatcher(),
            ),
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

/** Fake [ExerciseCategoryApi] so the ordered refresh can be driven end to end. */
private class StubCategoryApi : ExerciseCategoryApi {
    var response: List<ExerciseCategoryDto> = emptyList()
    var error: Throwable? = null

    override suspend fun getCategories(): List<ExerciseCategoryDto> {
        error?.let { throw it }
        return response
    }
}

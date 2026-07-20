package com.myfitnesslog.feature.exercise.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.myfitnesslog.core.data.local.MyFitnessLogDatabase
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.remote.ExerciseCategoryApi
import com.myfitnesslog.feature.exercise.data.remote.ExerciseCategoryDto
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
 * Repository tests over a real in-memory Room database (Robolectric) with a fake
 * Retrofit API. Verifies the download → upsert → observe pipeline and that API
 * failures never corrupt existing local data.
 */
@RunWith(RobolectricTestRunner::class)
class ExerciseCategoryRepositoryImplTest {

    private lateinit var database: MyFitnessLogDatabase
    private lateinit var api: FakeExerciseCategoryApi
    private lateinit var repository: ExerciseCategoryRepositoryImpl

    private val chestId = UUID.randomUUID()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyFitnessLogDatabase::class.java,
        ).build()
        api = FakeExerciseCategoryApi()
        repository = ExerciseCategoryRepositoryImpl(
            dao = database.exerciseCategoryDao(),
            api = api,
            ioDispatcher = UnconfinedTestDispatcher(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun refreshInsertsDownloadedCategoriesIntoRoom() = runTest {
        api.response = listOf(ExerciseCategoryDto(id = chestId.toString(), name = "Chest"))

        repository.refresh()

        assertEquals(
            listOf(ExerciseCategoryEntity(id = chestId, name = "Chest")),
            repository.observeAll().first(),
        )
    }

    @Test
    fun refreshUpsertsExistingRowByPrimaryKey() = runTest {
        api.response = listOf(ExerciseCategoryDto(id = chestId.toString(), name = "Chest"))
        repository.refresh()

        api.response = listOf(ExerciseCategoryDto(id = chestId.toString(), name = "Chest & Triceps"))
        repository.refresh()

        val all = repository.observeAll().first()
        assertEquals(1, all.size)
        assertEquals("Chest & Triceps", all.single().name)
    }

    @Test
    fun apiFailureDoesNotCorruptExistingLocalData() = runTest {
        api.response = listOf(ExerciseCategoryDto(id = chestId.toString(), name = "Chest"))
        repository.refresh()

        api.error = IOException("network down")
        assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { repository.refresh() }
        }

        // Previously downloaded data must remain intact.
        assertEquals(
            listOf(ExerciseCategoryEntity(id = chestId, name = "Chest")),
            repository.observeAll().first(),
        )
    }
}

/** Fake [ExerciseCategoryApi] driven by test-controlled state. */
private class FakeExerciseCategoryApi : ExerciseCategoryApi {
    var response: List<ExerciseCategoryDto> = emptyList()
    var error: Throwable? = null

    override suspend fun getCategories(): List<ExerciseCategoryDto> {
        error?.let { throw it }
        return response
    }
}

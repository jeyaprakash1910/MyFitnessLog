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
import com.myfitnesslog.core.data.local.WorkoutStatus
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutExerciseEntity
import com.myfitnesslog.feature.workout.data.local.WorkoutSessionEntity
import java.io.IOException
import java.time.Instant
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
    private val legsCategoryId = UUID.randomUUID()
    private val inclineId = UUID.randomUUID()

    private val NOW: Instant = Instant.parse("2026-07-22T09:00:00Z")

    private fun inclineDto(name: String) = ExerciseDto(
        id = inclineId.toString(),
        categoryId = categoryId.toString(),
        name = name,
    )

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
            categoryDao = database.exerciseCategoryDao(),
            database = database,
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

    // ---- Reconciliation (M11 Phase 2, defect D-3) -------------------------

    @Test
    fun `refreshLibrary removes exercises the backend no longer serves`() = runTest {
        val cold = coldStack()
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
        )
        cold.api.response = listOf(benchDto("Bench Press"), inclineDto("Incline Press"))
        cold.repository.refreshLibrary()
        assertEquals(2, cold.repository.observeAll().first().size)

        // The catalogue withdraws one exercise.
        cold.api.response = listOf(benchDto("Bench Press"))
        cold.repository.refreshLibrary()

        assertEquals(
            listOf("Bench Press"),
            cold.repository.observeAll().first().map { it.name },
        )
        cold.database.close()
    }

    @Test
    fun `refreshLibrary removes categories the backend no longer serves`() = runTest {
        val cold = coldStack()
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
            ExerciseCategoryDto(id = legsCategoryId.toString(), name = "Legs"),
        )
        cold.api.response = listOf(benchDto("Bench Press"))
        cold.repository.refreshLibrary()
        assertEquals(2, cold.database.exerciseCategoryDao().observeAll().first().size)

        // "Legs" is withdrawn. Nothing references it, so it must go — this is
        // exactly the case V6 had to work around by renaming in place.
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
        )
        cold.repository.refreshLibrary()

        assertEquals(
            listOf("Chest"),
            cold.database.exerciseCategoryDao().observeAll().first().map { it.name },
        )
        cold.database.close()
    }

    @Test
    fun `refreshLibrary keeps a withdrawn exercise that workout history references`() = runTest {
        // History is immutable (ADR-0001) and holds a RESTRICT foreign key to
        // the catalogue. A withdrawn exercise that appears in a past workout must
        // remain resolvable; it simply stops being offered for new work.
        val cold = coldStack()
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
        )
        cold.api.response = listOf(benchDto("Bench Press"))
        cold.repository.refreshLibrary()

        val sessionId = UUID.randomUUID()
        cold.database.workoutSessionDao().upsert(
            WorkoutSessionEntity(
                id = sessionId,
                routineId = null,
                status = WorkoutStatus.COMPLETED,
                startedAt = NOW,
                endedAt = NOW,
                notes = null,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )
        cold.database.workoutExerciseDao().upsert(
            WorkoutExerciseEntity(
                id = UUID.randomUUID(),
                workoutSessionId = sessionId,
                exerciseId = benchId,
                exerciseName = "Bench Press",
                exerciseOrder = 0,
                targetSets = 3,
                minTargetReps = 8,
                maxTargetReps = 12,
                targetRestSeconds = 90,
                notes = null,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        )

        // The catalogue drops it entirely.
        cold.api.response = emptyList()
        cold.repository.refreshLibrary()

        assertEquals(
            listOf("Bench Press"),
            cold.repository.observeAll().first().map { it.name },
        )
        cold.database.close()
    }

    @Test
    fun `refreshLibrary applies the backend category order`() = runTest {
        // Ordering is the catalogue's, not alphabetical (M11 Phase 2, D-2).
        val cold = coldStack()
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest", displayOrder = 1),
            ExerciseCategoryDto(id = legsCategoryId.toString(), name = "Back", displayOrder = 0),
        )
        cold.api.response = emptyList()

        cold.repository.refreshLibrary()

        assertEquals(
            listOf("Back", "Chest"),
            cold.database.exerciseCategoryDao().observeAll().first().map { it.name },
        )
        cold.database.close()
    }

    @Test
    fun `an empty catalogue response does not wipe the local library`() = runTest {
        // Reconciliation deletes rows the server no longer serves. A backend that
        // returns nothing — half-deployed, unseeded, misconfigured — would
        // therefore delete everything and leave the picker blank, the exact
        // failure that made the app unusable before M9.5 T2. An empty catalogue
        // is not a state this product can legitimately be in, so the destructive
        // half of the reconciliation is skipped.
        val cold = coldStack()
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
        )
        cold.api.response = listOf(benchDto("Bench Press"))
        cold.repository.refreshLibrary()
        assertEquals(1, cold.repository.observeAll().first().size)

        cold.categoryApi.response = emptyList()
        cold.api.response = emptyList()
        cold.repository.refreshLibrary()

        assertEquals(
            listOf("Bench Press"),
            cold.repository.observeAll().first().map { it.name },
        )
        assertEquals(1, cold.database.exerciseCategoryDao().observeAll().first().size)
        cold.database.close()
    }

    @Test
    fun `a healthy response after an empty one reconciles normally`() = runTest {
        // The guard must not become a permanent block on deletions.
        val cold = coldStack()
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
        )
        cold.api.response = listOf(benchDto("Bench Press"), inclineDto("Incline Press"))
        cold.repository.refreshLibrary()

        cold.api.response = emptyList()
        cold.categoryApi.response = emptyList()
        cold.repository.refreshLibrary()
        assertEquals(2, cold.repository.observeAll().first().size)

        // Server recovers, now genuinely serving one fewer exercise.
        cold.categoryApi.response = listOf(
            ExerciseCategoryDto(id = categoryId.toString(), name = "Chest"),
        )
        cold.api.response = listOf(benchDto("Bench Press"))
        cold.repository.refreshLibrary()

        assertEquals(
            listOf("Bench Press"),
            cold.repository.observeAll().first().map { it.name },
        )
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
                categoryDao = db.exerciseCategoryDao(),
                database = db,
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

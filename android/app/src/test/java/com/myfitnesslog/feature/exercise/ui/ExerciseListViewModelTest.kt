package com.myfitnesslog.feature.exercise.ui

import com.myfitnesslog.feature.exercise.data.ExerciseCategoryRepository
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.exercise.data.local.ExerciseCategoryEntity
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.UUID
import com.myfitnesslog.feature.routine.closeAndDrain
import com.myfitnesslog.feature.routine.tracked

/**
 * Unit tests for [ExerciseListViewModel] using fake repositories.
 *
 * Covers the list-state transitions (Loading / Success / Empty / Error), cached
 * data winning over a failed refresh, and the local search + category filtering
 * (search, category, combined, and clearing). Filtering never triggers refresh.
 */
class ExerciseListViewModelTest {

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private val chestId = UUID.randomUUID()
    private val backId = UUID.randomUUID()

    private val bench = exercise(chestId, "Bench Press")
    private val incline = exercise(chestId, "Incline Press")
    private val pullUp = exercise(backId, "Pull Up")

    private fun exercise(categoryId: UUID, name: String) = ExerciseEntity(
        id = UUID.randomUUID(),
        categoryId = categoryId,
        name = name,
        equipment = null,
    )

    private fun names(state: ExerciseLibraryUiState): List<String> =
        (state.listState as ExerciseListUiState.Success).exercises.map { it.name }

    // --- list-state transitions ---

    @Test
    fun showsLoadingWhileRefreshingAnEmptyCache() = runTest {
        val exercises = FakeExerciseRepository(emptyList()).apply {
            refreshGate = CompletableDeferred()
        }
        val viewModel = ExerciseListViewModel(exercises, FakeCategoryRepository()).tracked()

        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(ExerciseListUiState.Loading, viewModel.uiState.value.listState)
    }

    @Test
    fun reachesEmptyWhenRefreshSucceedsWithNoData() = runTest {
        val viewModel = ExerciseListViewModel(FakeExerciseRepository(emptyList()), FakeCategoryRepository()).tracked()

        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(ExerciseListUiState.Empty, viewModel.uiState.value.listState)
    }

    @Test
    fun reachesErrorWhenRefreshFailsWithNoCachedData() = runTest {
        val exercises = FakeExerciseRepository(emptyList()).apply {
            refreshError = IOException("network down")
        }
        val viewModel = ExerciseListViewModel(exercises, FakeCategoryRepository()).tracked()

        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertTrue(viewModel.uiState.value.listState is ExerciseListUiState.Error)
    }

    @Test
    fun keepsShowingCachedDataWhenRefreshFails() = runTest {
        val exercises = FakeExerciseRepository(listOf(bench)).apply {
            refreshError = IOException("network down")
        }
        val viewModel = ExerciseListViewModel(exercises, FakeCategoryRepository()).tracked()

        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertTrue(viewModel.uiState.value.listState is ExerciseListUiState.Success)
    }

    // --- local filtering ---

    @Test
    fun exposesCategoriesFromRepository() = runTest {
        val categories = FakeCategoryRepository(
            listOf(
                ExerciseCategoryEntity(chestId, "Chest"),
                ExerciseCategoryEntity(backId, "Back"),
            ),
        )
        val viewModel = ExerciseListViewModel(FakeExerciseRepository(listOf(bench)), categories).tracked()

        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        assertEquals(listOf("Chest", "Back"), viewModel.uiState.value.categories.map { it.name })
    }

    @Test
    fun searchFiltersListLocally() = runTest {
        val viewModel = seededViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChange("press")
        runCurrent()

        assertEquals(listOf("Bench Press", "Incline Press"), names(viewModel.uiState.value))
    }

    @Test
    fun categorySelectionFiltersListLocally() = runTest {
        val viewModel = seededViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onCategorySelected(backId)
        runCurrent()

        assertEquals(listOf("Pull Up"), names(viewModel.uiState.value))
        assertEquals(backId, viewModel.uiState.value.selectedCategoryId)
    }

    @Test
    fun searchAndCategoryCombineLocally() = runTest {
        val viewModel = seededViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onCategorySelected(chestId)
        viewModel.onQueryChange("incline")
        runCurrent()

        assertEquals(listOf("Incline Press"), names(viewModel.uiState.value))
    }

    @Test
    fun clearingFiltersRestoresFullList() = runTest {
        val viewModel = seededViewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onCategorySelected(chestId)
        viewModel.onQueryChange("incline")
        runCurrent()
        viewModel.onCategorySelected(null)
        viewModel.onQueryChange("")
        runCurrent()

        assertEquals(listOf("Bench Press", "Incline Press", "Pull Up"), names(viewModel.uiState.value))
    }

    private fun seededViewModel(): ExerciseListViewModel {
        val exercises = FakeExerciseRepository(listOf(bench, incline, pullUp))
        val categories = FakeCategoryRepository(
            listOf(ExerciseCategoryEntity(chestId, "Chest"), ExerciseCategoryEntity(backId, "Back")),
        )
        return ExerciseListViewModel(exercises, categories)
    }
}

/** In-memory fake replicating the DAO's filter semantics. */
private class FakeExerciseRepository(
    initial: List<ExerciseEntity>,
) : ExerciseRepository {

    private val exercises = MutableStateFlow(initial)
    var refreshError: Throwable? = null
    var refreshGate: CompletableDeferred<Unit>? = null
    var dataOnRefresh: List<ExerciseEntity>? = null

    override fun observeAll(): Flow<List<ExerciseEntity>> = exercises

    override fun observeById(id: UUID): Flow<ExerciseEntity?> =
        exercises.map { list -> list.firstOrNull { it.id == id } }

    override fun observeFiltered(categoryId: UUID?, query: String?): Flow<List<ExerciseEntity>> =
        exercises.map { list ->
            list.filter { entity ->
                (categoryId == null || entity.categoryId == categoryId) &&
                    (query == null || entity.name.contains(query, ignoreCase = true))
            }.sortedBy { it.name }
        }

    override suspend fun refreshLibrary() {
        refreshGate?.await()
        refreshError?.let { throw it }
        dataOnRefresh?.let { exercises.value = it }
    }
}

/** In-memory fake category repository. */
private class FakeCategoryRepository(
    initial: List<ExerciseCategoryEntity> = emptyList(),
) : ExerciseCategoryRepository {

    private val categories = MutableStateFlow(initial)

    override fun observeAll(): Flow<List<ExerciseCategoryEntity>> = categories

    override suspend fun fetch(): List<ExerciseCategoryEntity> = categories.value

    override fun observeById(id: UUID): Flow<ExerciseCategoryEntity?> =
        categories.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun refresh() = Unit
}

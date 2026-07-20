package com.myfitnesslog.feature.exercise.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.exercise.data.ExerciseCategoryRepository
import com.myfitnesslog.feature.exercise.data.ExerciseRepository
import com.myfitnesslog.feature.exercise.data.local.ExerciseEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the exercise-library screen.
 *
 * Search and category filtering are performed entirely against the local Room
 * cache through [ExerciseRepository.observeFiltered] — no network call, no
 * refresh is triggered by filtering (offline-first). A one-shot refresh runs
 * once on creation to populate/refresh the cache.
 *
 * Filter inputs ([query], [selectedCategoryId]) drive the exercise Flow via
 * flatMapLatest, and everything is combined into a single immutable
 * [ExerciseLibraryUiState]. Category names for the chips also come only from
 * Room via [ExerciseCategoryRepository].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExerciseListViewModel @Inject constructor(
    private val exerciseRepository: ExerciseRepository,
    private val categoryRepository: ExerciseCategoryRepository,
) : ViewModel() {

    private sealed interface RefreshState {
        data object Loading : RefreshState
        data object Idle : RefreshState
        data class Failed(val throwable: Throwable) : RefreshState
    }

    private val refreshState = MutableStateFlow<RefreshState>(RefreshState.Loading)
    private val query = MutableStateFlow("")
    private val selectedCategoryId = MutableStateFlow<UUID?>(null)

    private val filteredExercises =
        combine(query, selectedCategoryId) { q, categoryId -> q to categoryId }
            .flatMapLatest { (q, categoryId) ->
                exerciseRepository.observeFiltered(
                    categoryId = categoryId,
                    query = q.trim().ifBlank { null },
                )
            }

    val uiState: StateFlow<ExerciseLibraryUiState> =
        combine(
            query,
            selectedCategoryId,
            categoryRepository.observeAll(),
            filteredExercises,
            refreshState,
        ) { currentQuery, currentCategoryId, categories, exercises, refresh ->
            ExerciseLibraryUiState(
                query = currentQuery,
                categories = categories.map { CategoryFilterItem(it.id, it.name) },
                selectedCategoryId = currentCategoryId,
                listState = listState(exercises, refresh),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ExerciseLibraryUiState(),
        )

    init {
        refresh()
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun onCategorySelected(categoryId: UUID?) {
        selectedCategoryId.value = categoryId
    }

    private fun listState(
        exercises: List<ExerciseEntity>,
        refresh: RefreshState,
    ): ExerciseListUiState = when {
        exercises.isNotEmpty() -> ExerciseListUiState.Success(exercises.map { it.toListItem() })
        refresh is RefreshState.Loading -> ExerciseListUiState.Loading
        refresh is RefreshState.Failed ->
            ExerciseListUiState.Error(refresh.throwable.message ?: "Something went wrong.")
        else -> ExerciseListUiState.Empty
    }

    private fun refresh() {
        viewModelScope.launch {
            refreshState.value = RefreshState.Loading
            refreshState.value = try {
                exerciseRepository.refresh()
                RefreshState.Idle
            } catch (throwable: Throwable) {
                RefreshState.Failed(throwable)
            }
        }
    }
}

private fun ExerciseEntity.toListItem(): ExerciseListItem =
    ExerciseListItem(id = id, name = name, equipment = equipment)

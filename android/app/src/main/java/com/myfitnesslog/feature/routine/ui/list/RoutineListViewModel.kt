package com.myfitnesslog.feature.routine.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myfitnesslog.feature.routine.data.RoutineRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the routine list (Home). Observes routines from Room and exposes
 * an immutable state. Mutations are delegated to the repository; the list
 * updates reactively because Room is the source of truth.
 *
 * Creating a routine emits a one-shot [OpenEditor] event so the screen can
 * navigate into the new routine's editor to add exercises.
 */
@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val repository: RoutineRepository,
) : ViewModel() {

    /** One-shot navigation events. */
    data class OpenEditor(val routineId: UUID)

    private val _events = MutableSharedFlow<OpenEditor>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<RoutineListUiState> =
        repository.observeRoutines()
            .map { routines ->
                if (routines.isEmpty()) {
                    RoutineListUiState.Empty
                } else {
                    RoutineListUiState.Success(routines.map { RoutineListItem(it.id, it.name) })
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RoutineListUiState.Loading,
            )

    fun createRoutine(name: String) {
        viewModelScope.launch {
            val id = repository.createRoutine(name)
            _events.emit(OpenEditor(id))
        }
    }

    fun duplicateRoutine(id: UUID) {
        viewModelScope.launch { repository.duplicateRoutine(id) }
    }

    fun deleteRoutine(id: UUID) {
        viewModelScope.launch { repository.deleteRoutine(id) }
    }
}

package com.myfitnesslog.feature.routine.ui

/**
 * Route definitions for the routine feature. Kept in the feature so navigation
 * wiring stays feature-local; the app NavHost references these.
 */
object RoutineRoutes {
    const val ARG_ROUTINE_ID = "routineId"

    const val DETAIL = "routine/{routineId}"
    const val EDIT = "routine/{routineId}/edit"
    const val ADD_EXERCISE = "routine/{routineId}/add-exercise"

    fun detail(routineId: Any) = "routine/$routineId"
    fun edit(routineId: Any) = "routine/$routineId/edit"
    fun addExercise(routineId: Any) = "routine/$routineId/add-exercise"
}

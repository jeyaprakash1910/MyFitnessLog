package com.myfitnesslog.feature.workout.ui

/**
 * Routes for the workout feature. The active-workout screen is the top-level
 * "Workout" destination; it optionally accepts a routineId to start a workout
 * from a routine (otherwise it resumes the active session).
 */
object WorkoutRoutes {
    const val ARG_ROUTINE_ID = "routineId"
    const val ARG_SESSION_ID = "workoutSessionId"

    /** Base route used by the bottom-nav Workout tab (resume; no routine). */
    const val BASE = "workout"

    /** Full pattern registered in the graph (optional routineId query arg). */
    const val PATTERN = "workout?routineId={routineId}"

    /** Navigate here from a routine to start/resume a workout from it. */
    fun withRoutine(routineId: Any) = "workout?routineId=$routineId"

    /** Route for adding an exercise to an active (manual) workout — reuses the
     *  shared exercise picker. */
    const val ADD_EXERCISE = "workout/add-exercise/{workoutSessionId}"

    fun addExercise(sessionId: Any) = "workout/add-exercise/$sessionId"
}

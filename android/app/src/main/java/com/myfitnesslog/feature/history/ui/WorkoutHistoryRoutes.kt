package com.myfitnesslog.feature.history.ui

/**
 * Route definitions for the history feature. The list is the top-level "history"
 * destination (kept in [com.myfitnesslog.core.ui.navigation.TopLevelDestination]);
 * the detail screen is a full-screen drill-down keyed by the workout session id.
 */
object WorkoutHistoryRoutes {
    const val ARG_SESSION_ID = "sessionId"

    const val DETAIL = "history/{sessionId}"

    fun detail(sessionId: Any) = "history/$sessionId"
}

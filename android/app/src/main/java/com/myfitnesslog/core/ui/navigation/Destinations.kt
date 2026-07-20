package com.myfitnesslog.core.ui.navigation

/**
 * Top-level navigation destinations for Version 1, mirroring the screen
 * hierarchy in docs/ANDROID_FLOW.md.
 *
 * Phase 1 wires only the four top-level entries with placeholder content.
 * Nested destinations (Routine Details, Workout, Exercise Search, etc.) are
 * added in their respective feature milestones.
 */
enum class TopLevelDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    WORKOUT("workout", "Workout"),
    HISTORY("history", "History"),
    SETTINGS("settings", "Settings"),
}

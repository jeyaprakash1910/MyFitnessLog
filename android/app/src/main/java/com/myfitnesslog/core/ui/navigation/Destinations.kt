package com.myfitnesslog.core.ui.navigation

/**
 * Top-level navigation destinations for Version 1, mirroring the screen
 * hierarchy in docs/ANDROID_FLOW.md.
 *
 * Nested destinations (Routine Details, Workout, Exercise Picker, etc.) live in
 * their feature packages and are registered in MyFitnessLogNavHost.
 *
 * [EXERCISES] is the browsable exercise library (M4). It is distinct from the
 * exercise *picker*: the picker exists to choose one exercise for a routine or
 * an active workout, whereas this screen is read-only browsing with category
 * filtering, which the picker does not offer.
 */
enum class TopLevelDestination(val route: String, val label: String) {
    HOME("home", "Home"),
    WORKOUT("workout", "Workout"),
    EXERCISES("exercises", "Exercises"),
    HISTORY("history", "History"),
    SETTINGS("settings", "Settings"),
}

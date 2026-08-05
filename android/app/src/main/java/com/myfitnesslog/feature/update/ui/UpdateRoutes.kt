package com.myfitnesslog.feature.update.ui

/**
 * Navigation route for the update screen.
 *
 * Not a [com.myfitnesslog.core.ui.navigation.TopLevelDestination]: updates do not
 * warrant a permanent slot in the bottom bar. It is a drill-down reached from
 * Settings or from the update banner, so it renders full-screen with a back arrow
 * like the other detail destinations.
 */
object UpdateRoutes {
    const val UPDATE = "app-update"
}

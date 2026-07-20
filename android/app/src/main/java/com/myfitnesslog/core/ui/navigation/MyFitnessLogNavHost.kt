package com.myfitnesslog.core.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.myfitnesslog.feature.exercise.ui.ExerciseListScreen

/**
 * Root navigation graph for the application.
 *
 * Phase 1 exposes the four top-level destinations from [TopLevelDestination]
 * via a bottom navigation bar, each showing a [PlaceholderScreen]. This proves
 * the single-Activity / Navigation Compose setup works before any feature UI
 * exists.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFitnessLogNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination

    val currentTitle = TopLevelDestination.entries
        .firstOrNull { dest ->
            currentDestination?.hierarchy?.any { it.route == dest.route } == true
        }
        ?.label
        ?: TopLevelDestination.HOME.label

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(currentTitle) })
        },
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon(), contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            TopLevelDestination.entries.forEach { destination ->
                composable(destination.route) {
                    when (destination) {
                        // Home now shows the first real feature: the exercise library.
                        TopLevelDestination.HOME -> ExerciseListScreen()
                        // Remaining destinations stay as placeholders until their milestones.
                        else -> PlaceholderScreen(title = destination.label)
                    }
                }
            }
        }
    }
}

private fun TopLevelDestination.icon(): ImageVector = when (this) {
    TopLevelDestination.HOME -> Icons.Filled.Home
    TopLevelDestination.WORKOUT -> Icons.Filled.PlayArrow
    TopLevelDestination.HISTORY -> Icons.Filled.DateRange
    TopLevelDestination.SETTINGS -> Icons.Filled.Settings
}

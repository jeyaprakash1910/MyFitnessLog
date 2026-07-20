package com.myfitnesslog.core.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myfitnesslog.feature.routine.ui.RoutineRoutes
import com.myfitnesslog.feature.routine.ui.detail.RoutineDetailScreen
import com.myfitnesslog.feature.routine.ui.edit.RoutineEditScreen
import com.myfitnesslog.feature.routine.ui.list.RoutineListScreen
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerScreen

/**
 * Root navigation graph.
 *
 * Top-level destinations (Home/Workout/History/Settings) keep the bottom
 * navigation bar. Home hosts the routine list (ANDROID_FLOW: Home shows
 * routines). Drill-down routine destinations (detail, edit, add-exercise) render
 * full-screen: the bottom bar is hidden and the top bar shows a back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyFitnessLogNavHost() {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val topLevel = TopLevelDestination.entries.firstOrNull { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }
    val isTopLevel = topLevel != null && currentRoute in TopLevelDestination.entries.map { it.route }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(titleForRoute(currentRoute, topLevel)) },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTopLevel) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy?.any { it.route == destination.route } == true
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
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                RoutineListScreen(
                    onOpenRoutine = { id -> navController.navigate(RoutineRoutes.detail(id)) },
                    onEditRoutine = { id -> navController.navigate(RoutineRoutes.edit(id)) },
                )
            }
            composable(TopLevelDestination.WORKOUT.route) {
                PlaceholderScreen(title = TopLevelDestination.WORKOUT.label)
            }
            composable(TopLevelDestination.HISTORY.route) {
                PlaceholderScreen(title = TopLevelDestination.HISTORY.label)
            }
            composable(TopLevelDestination.SETTINGS.route) {
                PlaceholderScreen(title = TopLevelDestination.SETTINGS.label)
            }

            val routineIdArg = listOf(
                navArgument(RoutineRoutes.ARG_ROUTINE_ID) { type = NavType.StringType },
            )
            composable(RoutineRoutes.DETAIL, arguments = routineIdArg) { entry ->
                val id = entry.arguments?.getString(RoutineRoutes.ARG_ROUTINE_ID)
                RoutineDetailScreen(
                    onEditRoutine = { id?.let { navController.navigate(RoutineRoutes.edit(it)) } },
                )
            }
            composable(RoutineRoutes.EDIT, arguments = routineIdArg) { entry ->
                val id = entry.arguments?.getString(RoutineRoutes.ARG_ROUTINE_ID)
                RoutineEditScreen(
                    onAddExercise = { id?.let { navController.navigate(RoutineRoutes.addExercise(it)) } },
                )
            }
            composable(RoutineRoutes.ADD_EXERCISE, arguments = routineIdArg) {
                ExercisePickerScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}

private fun titleForRoute(route: String?, topLevel: TopLevelDestination?): String = when (route) {
    RoutineRoutes.DETAIL -> "Routine"
    RoutineRoutes.EDIT -> "Edit Routine"
    RoutineRoutes.ADD_EXERCISE -> "Add Exercise"
    else -> topLevel?.label ?: TopLevelDestination.HOME.label
}

private fun TopLevelDestination.icon(): ImageVector = when (this) {
    TopLevelDestination.HOME -> Icons.Filled.Home
    TopLevelDestination.WORKOUT -> Icons.Filled.PlayArrow
    TopLevelDestination.HISTORY -> Icons.Filled.DateRange
    TopLevelDestination.SETTINGS -> Icons.Filled.Settings
}

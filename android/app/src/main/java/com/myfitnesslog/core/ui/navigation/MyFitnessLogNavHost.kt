package com.myfitnesslog.core.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
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
import com.myfitnesslog.feature.exercise.ui.ExerciseListScreen
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.myfitnesslog.feature.history.ui.WorkoutHistoryRoutes
import com.myfitnesslog.feature.history.ui.WorkoutHistoryScreen
import com.myfitnesslog.feature.history.ui.detail.WorkoutDetailScreen
import com.myfitnesslog.feature.routine.ui.RoutineRoutes
import com.myfitnesslog.feature.routine.ui.detail.RoutineDetailScreen
import com.myfitnesslog.feature.routine.ui.edit.RoutineEditScreen
import com.myfitnesslog.feature.routine.ui.list.RoutineListScreen
import com.myfitnesslog.feature.routine.ui.picker.ExercisePickerScreen
import com.myfitnesslog.feature.settings.ui.SettingsScreen
import com.myfitnesslog.feature.update.ui.UpdateBanner
import com.myfitnesslog.feature.update.ui.UpdateRoutes
import com.myfitnesslog.feature.update.ui.UpdateScreen
import com.myfitnesslog.feature.workout.ui.WorkoutRoutes
import com.myfitnesslog.feature.workout.ui.WorkoutScreen
import com.myfitnesslog.feature.workout.ui.WorkoutViewModel
import com.myfitnesslog.feature.workout.ui.indicator.WorkoutIndicator

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

    // Compare on the base route (before any "?query") so parameterised top-level
    // routes such as the workout screen still register as top-level.
    fun String?.base() = this?.substringBefore("?")
    val topLevel = TopLevelDestination.entries.firstOrNull { dest ->
        currentDestination?.hierarchy?.any { it.route.base() == dest.route } == true
    }
    val isTopLevel = topLevel != null && currentRoute.base() in TopLevelDestination.entries.map { it.route }

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
                Column {
                    // Persistent workout indicator: shown on top-level screens other
                    // than the Workout screen itself; tapping resumes the workout.
                    if (currentRoute.base() != TopLevelDestination.WORKOUT.route) {
                        WorkoutIndicator(
                            onClick = {
                                navController.navigate(TopLevelDestination.WORKOUT.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                        )
                    }
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                        val selected = currentDestination
                            ?.hierarchy?.any { it.route.base() == destination.route } == true
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
            }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding)) {
            // Update notice: the only thing that tells the user a newer build
            // exists (ADR-0016). Shown on top-level screens only, and never on the
            // Workout screen (mid-set is the wrong moment to mention an update),
            // nor on the update screen itself, which already says so.
            if (isTopLevel && currentRoute.base() != TopLevelDestination.WORKOUT.route) {
                UpdateBanner(onClick = { navController.navigate(UpdateRoutes.UPDATE) })
            }
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.HOME.route,
            ) {
                composable(TopLevelDestination.HOME.route) {
                    RoutineListScreen(
                        onOpenRoutine = { id -> navController.navigate(RoutineRoutes.detail(id)) },
                        onEditRoutine = { id -> navController.navigate(RoutineRoutes.edit(id)) },
                    )
                }
                composable(
                    route = WorkoutRoutes.PATTERN,
                    arguments = listOf(
                        navArgument(WorkoutRoutes.ARG_ROUTINE_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    WorkoutScreen(
                        onFinished = { event ->
                            when (event) {
                                WorkoutViewModel.Event.COMPLETED ->
                                    navController.navigate(TopLevelDestination.HISTORY.route) {
                                        popUpTo(TopLevelDestination.HOME.route)
                                        launchSingleTop = true
                                    }
                                WorkoutViewModel.Event.DISCARDED ->
                                    navController.navigate(TopLevelDestination.HOME.route) {
                                        popUpTo(TopLevelDestination.HOME.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                            }
                        },
                        onAddExercise = { sessionId ->
                            navController.navigate(WorkoutRoutes.addExercise(sessionId))
                        },
                    )
                }
                composable(
                    route = WorkoutRoutes.ADD_EXERCISE,
                    arguments = listOf(navArgument(WorkoutRoutes.ARG_SESSION_ID) { type = NavType.StringType }),
                ) {
                    ExercisePickerScreen(onDone = { navController.popBackStack() })
                }
                composable(TopLevelDestination.EXERCISES.route) {
                    ExerciseListScreen()
                }
                composable(TopLevelDestination.HISTORY.route) {
                    WorkoutHistoryScreen(
                        onOpenWorkout = { id -> navController.navigate(WorkoutHistoryRoutes.detail(id)) },
                    )
                }
                composable(
                    route = WorkoutHistoryRoutes.DETAIL,
                    arguments = listOf(navArgument(WorkoutHistoryRoutes.ARG_SESSION_ID) { type = NavType.StringType }),
                ) {
                    WorkoutDetailScreen()
                }
                composable(TopLevelDestination.SETTINGS.route) {
                    SettingsScreen(
                        onOpenAppUpdate = { navController.navigate(UpdateRoutes.UPDATE) },
                    )
                }
                composable(UpdateRoutes.UPDATE) {
                    UpdateScreen()
                }

                val routineIdArg = listOf(
                    navArgument(RoutineRoutes.ARG_ROUTINE_ID) { type = NavType.StringType },
                )
                composable(RoutineRoutes.DETAIL, arguments = routineIdArg) { entry ->
                    val id = entry.arguments?.getString(RoutineRoutes.ARG_ROUTINE_ID)
                    RoutineDetailScreen(
                        onEditRoutine = { id?.let { navController.navigate(RoutineRoutes.edit(it)) } },
                        onStartWorkout = { id?.let { navController.navigate(WorkoutRoutes.withRoutine(it)) } },
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
}

private fun titleForRoute(route: String?, topLevel: TopLevelDestination?): String = when (route) {
    RoutineRoutes.DETAIL -> "Routine"
    RoutineRoutes.EDIT -> "Edit Routine"
    RoutineRoutes.ADD_EXERCISE -> "Add Exercise"
    WorkoutHistoryRoutes.DETAIL -> "Workout"
    UpdateRoutes.UPDATE -> "App Update"
    else -> topLevel?.label ?: TopLevelDestination.HOME.label
}

private fun TopLevelDestination.icon(): ImageVector = when (this) {
    TopLevelDestination.HOME -> Icons.Filled.Home
    TopLevelDestination.WORKOUT -> Icons.Filled.PlayArrow
    TopLevelDestination.EXERCISES -> Icons.AutoMirrored.Filled.List
    TopLevelDestination.HISTORY -> Icons.Filled.DateRange
    TopLevelDestination.SETTINGS -> Icons.Filled.Settings
}

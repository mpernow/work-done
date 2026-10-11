package com.mpernow.workdone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mpernow.workdone.ui.exercises.ExerciseDetailScreen
import com.mpernow.workdone.ui.exercises.ExerciseEditScreen
import com.mpernow.workdone.ui.exercises.ExercisesScreen
import com.mpernow.workdone.ui.history.HistoryScreen
import com.mpernow.workdone.ui.log.LogWorkoutScreen
import com.mpernow.workdone.ui.settings.SettingsScreen
import com.mpernow.workdone.ui.stats.StatsScreen

@Composable
fun WorkDoneNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = LogRoute, modifier = modifier) {
        composable<LogRoute> {
            LogWorkoutScreen(onCreateExercise = { name -> navController.navigate(ExerciseEditRoute(initialName = name)) })
        }
        composable<HistoryRoute> { HistoryScreen() }
        composable<ExercisesRoute> {
            ExercisesScreen(
                onExerciseClick = { id -> navController.navigate(ExerciseDetailRoute(id)) },
                onAddExercise = { navController.navigate(ExerciseEditRoute()) },
            )
        }
        composable<ExerciseDetailRoute> { entry ->
            val exerciseId = entry.toRoute<ExerciseDetailRoute>().exerciseId
            ExerciseDetailScreen(
                exerciseId = exerciseId,
                onNavigateUp = { navController.navigateUp() },
                onEdit = { navController.navigate(ExerciseEditRoute(exerciseId = exerciseId)) },
            )
        }
        composable<ExerciseEditRoute> { entry ->
            val route = entry.toRoute<ExerciseEditRoute>()
            ExerciseEditScreen(
                exerciseId = route.exerciseId,
                initialName = route.initialName,
                onSaved = { navController.navigateUp() },
                // The detail screen of a deleted exercise has nothing left to show, so close it too.
                onDeleted = {
                    if (!navController.popBackStack<ExerciseDetailRoute>(inclusive = true)) navController.navigateUp()
                },
                onNavigateUp = { navController.navigateUp() },
            )
        }
        composable<StatsRoute> { StatsScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}

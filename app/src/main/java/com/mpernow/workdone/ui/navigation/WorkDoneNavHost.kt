package com.mpernow.workdone.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.mpernow.workdone.ui.exercises.ExerciseDetailScreen
import com.mpernow.workdone.ui.exercises.ExercisesScreen
import com.mpernow.workdone.ui.history.HistoryScreen
import com.mpernow.workdone.ui.log.LogWorkoutScreen
import com.mpernow.workdone.ui.settings.SettingsScreen
import com.mpernow.workdone.ui.stats.StatsScreen

@Composable
fun WorkDoneNavHost(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = LogRoute, modifier = modifier) {
        composable<LogRoute> { LogWorkoutScreen() }
        composable<HistoryRoute> { HistoryScreen() }
        composable<ExercisesRoute> {
            ExercisesScreen(onExerciseClick = { id -> navController.navigate(ExerciseDetailRoute(id)) })
        }
        composable<ExerciseDetailRoute> { entry ->
            ExerciseDetailScreen(exerciseId = entry.toRoute<ExerciseDetailRoute>().exerciseId)
        }
        composable<StatsRoute> { StatsScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}

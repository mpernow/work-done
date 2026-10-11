package com.mpernow.workdone.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// Type-safe Navigation Compose routes.
@Serializable object LogRoute
@Serializable object HistoryRoute
@Serializable object ExercisesRoute
@Serializable object StatsRoute
@Serializable object SettingsRoute
@Serializable data class ExerciseDetailRoute(val exerciseId: Long)

/** The tabs shown in the bottom navigation bar, in order. */
enum class TopLevelDestination(val route: Any, val label: String, val icon: ImageVector) {
    LOG(LogRoute, "Log", Icons.Filled.Add),
    HISTORY(HistoryRoute, "History", Icons.AutoMirrored.Filled.List),
    EXERCISES(ExercisesRoute, "Exercises", Icons.Filled.FitnessCenter),
    STATS(StatsRoute, "Stats", Icons.Filled.BarChart),
    SETTINGS(SettingsRoute, "Settings", Icons.Filled.Settings),
}

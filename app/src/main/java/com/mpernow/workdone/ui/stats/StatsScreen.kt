package com.mpernow.workdone.ui.stats

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.PlaceholderScreen

// Phase 5: workouts per week, volume over time, heatmap, streak, category split.
@Composable
fun StatsScreen() {
    AppScaffold(title = "Statistics") { padding ->
        PlaceholderScreen("Coming soon", "Charts appear once you've logged some workouts.", Modifier.padding(padding))
    }
}

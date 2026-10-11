package com.mpernow.workdone.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.WorkoutEntity
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.domain.WorkoutDates
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.PlaceholderScreen
import com.mpernow.workdone.ui.common.appViewModelFactory
import com.mpernow.workdone.ui.common.formatDate
import com.mpernow.workdone.ui.common.formatTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

data class WorkoutSummary(
    val workout: WorkoutEntity,
    val setCount: Int,
    val exerciseNames: List<String>,
)

data class HistorySection(val label: String, val workouts: List<WorkoutSummary>)

class HistoryViewModel(workoutRepository: WorkoutRepository) : ViewModel() {
    /** Finished workouts, newest first, grouped by week. The one in progress is on the Log tab. */
    val sections: StateFlow<List<HistorySection>?> = combine(
        workoutRepository.observeFinishedWorkouts(),
        workoutRepository.observeExerciseSummaries(),
    ) { workouts, exerciseSummaries ->
        val byWorkout = exerciseSummaries.groupBy { it.workoutId }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        workouts
            .map { workout ->
                val exercises = byWorkout[workout.id].orEmpty().sortedBy { it.firstPosition }
                WorkoutSummary(workout, exercises.sumOf { it.setCount }, exercises.map { it.exerciseName })
            }
            .groupBy { WorkoutDates.weekStart(it.workout.startedAt.atZone(zone).toLocalDate()) }
            .map { (weekStart, summaries) -> HistorySection(WorkoutDates.weekLabel(weekStart, today), summaries) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HistoryScreen(
    onWorkoutClick: (Long) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = appViewModelFactory { HistoryViewModel(it.workoutRepository) }),
) {
    val sections by viewModel.sections.collectAsStateWithLifecycle()
    AppScaffold(title = "History") { padding ->
        val loaded = sections ?: return@AppScaffold
        if (loaded.isEmpty()) {
            PlaceholderScreen("No workouts yet", "Finished workouts show up here.", Modifier.padding(padding))
            return@AppScaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            loaded.forEach { section ->
                item(key = "header-${section.label}") {
                    Text(
                        section.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    )
                }
                items(section.workouts, key = { it.workout.id }) { summary ->
                    WorkoutRow(summary, onClick = { onWorkoutClick(summary.workout.id) })
                }
            }
        }
    }
}

@Composable
private fun WorkoutRow(summary: WorkoutSummary, onClick: () -> Unit) {
    val workout = summary.workout
    val details = buildList {
        workout.finishedAt?.let { add(WorkoutDates.formatDuration(Duration.between(workout.startedAt, it))) }
        add(if (summary.setCount == 1) "1 set" else "${summary.setCount} sets")
    }.joinToString(" · ")
    ListItem(
        headlineContent = { Text("${formatDate(workout.startedAt)} · ${formatTime(workout.startedAt)}") },
        supportingContent = {
            Text(
                listOf(details, summary.exerciseNames.joinToString(", "), workout.notes)
                    .filter { it.isNotBlank() }
                    .joinToString("\n"),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

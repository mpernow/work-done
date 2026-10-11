package com.mpernow.workdone.ui.history

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.WorkoutEntity
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.ui.common.PlaceholderScreen
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class HistoryViewModel(workoutRepository: WorkoutRepository) : ViewModel() {
    val workouts: StateFlow<List<WorkoutEntity>> = workoutRepository.observeWorkouts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

private val dateFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

// Phase 4: calendar view, edit and delete.
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = viewModel(factory = appViewModelFactory { HistoryViewModel(it.workoutRepository) }),
) {
    val workouts by viewModel.workouts.collectAsStateWithLifecycle()
    if (workouts.isEmpty()) {
        PlaceholderScreen("History", "No workouts logged yet.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(workouts, key = { it.id }) { workout ->
            ListItem(
                headlineContent = { Text(dateFormatter.format(workout.startedAt.atZone(ZoneId.systemDefault()))) },
                supportingContent = workout.notes.takeIf { it.isNotBlank() }?.let { { Text(it) } },
            )
        }
    }
}

package com.mpernow.workdone.ui.exercises

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.PlaceholderScreen
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExercisesViewModel(exerciseRepository: ExerciseRepository) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>> = exerciseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ExercisesScreen(
    onExerciseClick: (Long) -> Unit,
    onAddExercise: () -> Unit,
    viewModel: ExercisesViewModel = viewModel(factory = appViewModelFactory { ExercisesViewModel(it.exerciseRepository) }),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    AppScaffold(
        title = "Exercises",
        floatingActionButton = {
            FloatingActionButton(onClick = onAddExercise) {
                Icon(Icons.Filled.Add, contentDescription = "New exercise")
            }
        },
    ) { padding ->
        if (exercises.isEmpty()) {
            PlaceholderScreen("No exercises yet", "Tap + to add one.", Modifier.padding(padding))
            return@AppScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 88.dp), // keep the last row clear of the FAB
        ) {
            items(exercises, key = { it.id }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = { Text(exercise.trackingType.label) },
                    modifier = Modifier.clickable { onExerciseClick(exercise.id) },
                )
            }
        }
    }
}

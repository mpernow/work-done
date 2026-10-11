package com.mpernow.workdone.ui.exercises

import androidx.compose.foundation.clickable
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
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.ui.common.PlaceholderScreen
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ExercisesViewModel(exerciseRepository: ExerciseRepository) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>> = exerciseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

// Phase 3: create and edit exercises.
@Composable
fun ExercisesScreen(
    onExerciseClick: (Long) -> Unit,
    viewModel: ExercisesViewModel = viewModel(factory = appViewModelFactory { ExercisesViewModel(it.exerciseRepository) }),
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    if (exercises.isEmpty()) {
        PlaceholderScreen("Exercises", "No exercises yet.")
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(exercises, key = { it.id }) { exercise ->
            ListItem(
                headlineContent = { Text(exercise.name) },
                supportingContent = { Text(exercise.category.name.lowercase().replaceFirstChar(Char::uppercase)) },
                modifier = Modifier.clickable { onExerciseClick(exercise.id) },
            )
        }
    }
}

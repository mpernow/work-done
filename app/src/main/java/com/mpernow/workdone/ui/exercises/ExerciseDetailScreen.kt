package com.mpernow.workdone.ui.exercises

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

class ExerciseDetailViewModel(exerciseRepository: ExerciseRepository, exerciseId: Long) : ViewModel() {
    val exercise: StateFlow<ExerciseEntity?> = exerciseRepository.observe(exerciseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

// Phase 5: max weight, estimated 1RM, volume charts and personal records.
@Composable
fun ExerciseDetailScreen(
    exerciseId: Long,
    onNavigateUp: () -> Unit,
    onEdit: () -> Unit,
    viewModel: ExerciseDetailViewModel = viewModel(
        factory = appViewModelFactory { ExerciseDetailViewModel(it.exerciseRepository, exerciseId) },
    ),
) {
    val exercise by viewModel.exercise.collectAsStateWithLifecycle()
    AppScaffold(
        title = exercise?.name.orEmpty(),
        onNavigateUp = onNavigateUp,
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit exercise") }
        },
    ) { padding ->
        PlaceholderScreen("Progress", "Charts and personal records are coming.", Modifier.padding(padding))
    }
}

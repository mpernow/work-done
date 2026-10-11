package com.mpernow.workdone.ui.exercises

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.data.repository.SaveExerciseResult
import com.mpernow.workdone.domain.TrackingType
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditOutcome { SAVED, DELETED }

data class ExerciseEditUiState(
    val name: String = "",
    val trackingType: TrackingType = TrackingType.WEIGHT_REPS,
    val isNew: Boolean = true,
    val loading: Boolean = false,
    /** Sets already logged; an exercise in use can't change tracking type or be deleted. */
    val setCount: Int = 0,
    val nameError: String? = null,
    val outcome: EditOutcome? = null,
)

class ExerciseEditViewModel(
    private val exerciseRepository: ExerciseRepository,
    private val exerciseId: Long,
    initialName: String,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ExerciseEditUiState(name = initialName, isNew = exerciseId == 0L, loading = exerciseId != 0L),
    )
    val state: StateFlow<ExerciseEditUiState> = _state.asStateFlow()

    init {
        if (exerciseId != 0L) {
            viewModelScope.launch {
                val exercise = exerciseRepository.get(exerciseId)
                val setCount = exerciseRepository.countSets(exerciseId)
                _state.update {
                    if (exercise == null) {
                        it.copy(loading = false, outcome = EditOutcome.DELETED)
                    } else {
                        it.copy(
                            name = exercise.name,
                            trackingType = exercise.trackingType,
                            setCount = setCount,
                            loading = false,
                        )
                    }
                }
            }
        }
    }

    fun setName(name: String) = _state.update { it.copy(name = name, nameError = null) }

    fun setTrackingType(type: TrackingType) = _state.update { if (it.setCount == 0) it.copy(trackingType = type) else it }

    fun save() {
        val state = _state.value
        val name = state.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(nameError = "Enter a name") }
            return
        }
        viewModelScope.launch {
            when (exerciseRepository.save(ExerciseEntity(exerciseId, name, state.trackingType))) {
                is SaveExerciseResult.Saved -> _state.update { it.copy(outcome = EditOutcome.SAVED) }
                SaveExerciseResult.NameTaken -> _state.update { it.copy(nameError = "There's already an exercise called $name") }
            }
        }
    }

    fun delete() {
        val state = _state.value
        viewModelScope.launch {
            val deleted = exerciseRepository.delete(ExerciseEntity(exerciseId, state.name, state.trackingType))
            if (deleted) _state.update { it.copy(outcome = EditOutcome.DELETED) }
        }
    }
}

@Composable
fun ExerciseEditScreen(
    exerciseId: Long,
    initialName: String,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: ExerciseEditViewModel = viewModel(
        factory = appViewModelFactory { ExerciseEditViewModel(it.exerciseRepository, exerciseId, initialName) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val inUse = state.setCount > 0

    LaunchedEffect(state.outcome) {
        when (state.outcome) {
            EditOutcome.SAVED -> onSaved()
            EditOutcome.DELETED -> onDeleted()
            null -> Unit
        }
    }

    AppScaffold(
        title = if (state.isNew) "New exercise" else "Edit exercise",
        onNavigateUp = onNavigateUp,
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { confirmDelete = true }, enabled = !inUse && !state.loading) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete exercise")
                }
            }
            TextButton(onClick = viewModel::save, enabled = !state.loading) { Text("Save") }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Name") },
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Tracked as", style = MaterialTheme.typography.titleSmall)
            Column(Modifier.selectableGroup()) {
                TrackingType.entries.forEach { type ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = type == state.trackingType,
                                enabled = !inUse,
                                role = Role.RadioButton,
                                onClick = { viewModel.setTrackingType(type) },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        RadioButton(selected = type == state.trackingType, onClick = null, enabled = !inUse)
                        Text(type.label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (inUse) {
                Text(
                    "Used in ${state.setCount} logged sets, so the tracking type can't be changed " +
                        "and the exercise can't be deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${state.name}?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

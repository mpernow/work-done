package com.mpernow.workdone.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.WorkoutEntity
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetValues
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.domain.WorkoutDates
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.DateTimePickerDialog
import com.mpernow.workdone.ui.common.appViewModelFactory
import com.mpernow.workdone.ui.common.formatDate
import com.mpernow.workdone.ui.common.formatDateTime
import com.mpernow.workdone.ui.workout.AddExerciseButton
import com.mpernow.workdone.ui.workout.EditSetDialog
import com.mpernow.workdone.ui.workout.ExerciseBlock
import com.mpernow.workdone.ui.workout.ExerciseCard
import com.mpernow.workdone.ui.workout.ExercisePickerSheet
import com.mpernow.workdone.ui.workout.WorkoutSetsEditor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

sealed interface WorkoutDetailUiState {
    data object Loading : WorkoutDetailUiState
    data object Deleted : WorkoutDetailUiState
    data class Loaded(
        val workout: WorkoutEntity,
        val blocks: List<ExerciseBlock>,
        val weightUnit: WeightUnit,
    ) : WorkoutDetailUiState
}

class WorkoutDetailViewModel(
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    settingsRepository: SettingsRepository,
    workoutId: Long,
) : ViewModel() {
    /** Most used first, which is the order the exercise picker shows them in. */
    val exercises: StateFlow<List<ExerciseEntity>> = exerciseRepository.observeByUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val editor = WorkoutSetsEditor(viewModelScope, workoutRepository, settingsRepository, exercises)

    val uiState: StateFlow<WorkoutDetailUiState> = combine(
        workoutRepository.observeWorkout(workoutId),
        editor.observe(workoutId),
    ) { workout, sets ->
        if (workout == null) {
            WorkoutDetailUiState.Deleted
        } else {
            WorkoutDetailUiState.Loaded(workout, sets.blocks, sets.weightUnit)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorkoutDetailUiState.Loading)

    private val workout: WorkoutEntity?
        get() = (uiState.value as? WorkoutDetailUiState.Loaded)?.workout

    /** Moving the start moves the end by the same amount, so the duration stays the same. */
    fun setStartTime(startedAt: Instant) {
        val workout = workout ?: return
        val shift = Duration.between(workout.startedAt, startedAt)
        save(workout.copy(startedAt = startedAt, finishedAt = workout.finishedAt?.plus(shift)))
    }

    fun setEndTime(finishedAt: Instant) {
        val workout = workout ?: return
        save(workout.copy(finishedAt = finishedAt))
    }

    fun setNotes(notes: String) {
        val workout = workout ?: return
        save(workout.copy(notes = notes.trim()))
    }

    fun deleteWorkout() {
        val workout = workout ?: return
        viewModelScope.launch { workoutRepository.deleteWorkout(workout) }
    }

    private fun save(workout: WorkoutEntity) {
        viewModelScope.launch { workoutRepository.saveWorkout(workout) }
    }

    fun addExercise(exerciseId: Long) = editor.addExercise(exerciseId)

    fun updateDraft(exerciseId: Long, draft: SetDraft) = editor.updateDraft(exerciseId, draft)

    fun addSet(exerciseId: Long) {
        val state = uiState.value as? WorkoutDetailUiState.Loaded ?: return
        val block = state.blocks.firstOrNull { it.exercise.id == exerciseId } ?: return
        viewModelScope.launch { editor.addSet(state.workout.id, block, state.weightUnit) }
    }

    fun updateSet(set: SetEntryEntity, values: SetValues) = editor.updateSet(set, values)

    fun deleteSet(set: SetEntryEntity) = editor.deleteSet(set)
}

private enum class TimeField { START, END }

@Composable
fun WorkoutDetailScreen(
    workoutId: Long,
    onNavigateUp: () -> Unit,
    onCreateExercise: (String) -> Unit,
    viewModel: WorkoutDetailViewModel = viewModel(
        factory = appViewModelFactory {
            WorkoutDetailViewModel(it.workoutRepository, it.exerciseRepository, it.settingsRepository, workoutId)
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (state == WorkoutDetailUiState.Deleted) onNavigateUp()
    }
    when (val current = state) {
        WorkoutDetailUiState.Loading, WorkoutDetailUiState.Deleted ->
            AppScaffold(title = "Workout", onNavigateUp = onNavigateUp) {}
        is WorkoutDetailUiState.Loaded -> WorkoutDetail(current, viewModel, onNavigateUp, onCreateExercise)
    }
}

@Composable
private fun WorkoutDetail(
    state: WorkoutDetailUiState.Loaded,
    viewModel: WorkoutDetailViewModel,
    onNavigateUp: () -> Unit,
    onCreateExercise: (String) -> Unit,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val workout = state.workout
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var editingNotes by rememberSaveable { mutableStateOf(false) }
    var editingTime by rememberSaveable { mutableStateOf<TimeField?>(null) }
    var editingSetId by rememberSaveable { mutableStateOf<Long?>(null) }

    AppScaffold(
        title = formatDate(workout.startedAt),
        onNavigateUp = onNavigateUp,
        actions = {
            IconButton(onClick = { showDelete = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete workout")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    Column {
                        ListItem(
                            headlineContent = { Text("Started ${formatDateTime(workout.startedAt)}") },
                            leadingContent = { Icon(Icons.Filled.Schedule, contentDescription = null) },
                            colors = itemColors,
                            modifier = Modifier.clickable { editingTime = TimeField.START },
                        )
                        workout.finishedAt?.let { finishedAt ->
                            ListItem(
                                headlineContent = { Text("Ended ${formatDateTime(finishedAt)}") },
                                supportingContent = {
                                    Text(WorkoutDates.formatDuration(Duration.between(workout.startedAt, finishedAt)))
                                },
                                leadingContent = { Icon(Icons.Filled.Timer, contentDescription = null) },
                                colors = itemColors,
                                modifier = Modifier.clickable { editingTime = TimeField.END },
                            )
                        }
                        ListItem(
                            headlineContent = { Text(workout.notes.ifBlank { "Add notes" }) },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null) },
                            colors = itemColors,
                            modifier = Modifier.clickable { editingNotes = true },
                        )
                    }
                }
            }
            items(state.blocks, key = { it.exercise.id }) { block ->
                ExerciseCard(
                    block = block,
                    unit = state.weightUnit,
                    onDraftChange = { viewModel.updateDraft(block.exercise.id, it) },
                    onAddSet = { viewModel.addSet(block.exercise.id) },
                    onEditSet = { editingSetId = it.id },
                    onDeleteSet = viewModel::deleteSet,
                )
            }
            item { AddExerciseButton(onClick = { showPicker = true }) }
        }
    }

    when (editingTime) {
        TimeField.START -> DateTimePickerDialog(
            title = "Started",
            initial = workout.startedAt,
            // The end moves along with the start, so it must stay in the past too.
            max = Instant.now().minus(Duration.between(workout.startedAt, workout.finishedAt ?: workout.startedAt)),
            onConfirm = { viewModel.setStartTime(it); editingTime = null },
            onDismiss = { editingTime = null },
        )
        TimeField.END -> DateTimePickerDialog(
            title = "Ended",
            initial = workout.finishedAt ?: workout.startedAt,
            min = workout.startedAt,
            max = Instant.now(),
            onConfirm = { viewModel.setEndTime(it); editingTime = null },
            onDismiss = { editingTime = null },
        )
        null -> Unit
    }
    if (editingNotes) {
        NotesDialog(
            initialNotes = workout.notes,
            onSave = { viewModel.setNotes(it); editingNotes = false },
            onDismiss = { editingNotes = false },
        )
    }
    if (showPicker) {
        ExercisePickerSheet(
            exercises = exercises,
            alreadyAdded = state.blocks.map { it.exercise.id }.toSet(),
            onPick = { viewModel.addExercise(it); showPicker = false },
            onCreate = { showPicker = false; onCreateExercise(it) },
            onDismiss = { showPicker = false },
        )
    }
    editingSetId?.let { setId ->
        val block = state.blocks.firstOrNull { block -> block.sets.any { it.id == setId } }
        val set = block?.sets?.first { it.id == setId }
        // A set deleted while its dialog was open simply closes the dialog.
        if (block != null && set != null) {
            EditSetDialog(
                set = set,
                exercise = block.exercise,
                unit = state.weightUnit,
                onSave = { viewModel.updateSet(set, it); editingSetId = null },
                onDismiss = { editingSetId = null },
            )
        }
    }
    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete workout?") },
            text = { Text("The workout and all its sets will be deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { showDelete = false; viewModel.deleteWorkout() }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NotesDialog(initialNotes: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notes") },
        text = {
            OutlinedTextField(value = notes, onValueChange = { notes = it }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSave(notes) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

package com.mpernow.workdone.ui.log

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.WorkoutDates
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.DateTimePickerDialog
import com.mpernow.workdone.ui.common.appViewModelFactory
import com.mpernow.workdone.ui.common.formatDateTime
import com.mpernow.workdone.ui.workout.AddExerciseButton
import com.mpernow.workdone.ui.workout.EditSetDialog
import com.mpernow.workdone.ui.workout.ExerciseCard
import com.mpernow.workdone.ui.workout.ExercisePickerSheet
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun LogWorkoutScreen(
    onCreateExercise: (String) -> Unit,
    onAddFromPhoto: (workoutId: Long) -> Unit,
    viewModel: LogWorkoutViewModel = viewModel(
        factory = appViewModelFactory {
            LogWorkoutViewModel(it.workoutRepository, it.exerciseRepository, it.settingsRepository)
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    when (val current = state) {
        LogUiState.Loading -> AppScaffold(title = "Workout") {}
        LogUiState.Idle -> IdleContent(onStart = viewModel::startWorkout)
        is LogUiState.Active -> ActiveWorkout(current, viewModel, onCreateExercise, onAddFromPhoto)
    }
}

@Composable
private fun IdleContent(onStart: () -> Unit) {
    AppScaffold(title = "Workout") { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("No workout in progress", style = MaterialTheme.typography.titleMedium)
            Button(onClick = onStart, modifier = Modifier.padding(top = 16.dp)) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Start workout")
            }
            Text(
                "To log a past workout, start one and change its start time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun ActiveWorkout(
    state: LogUiState.Active,
    viewModel: LogWorkoutViewModel,
    onCreateExercise: (String) -> Unit,
    onAddFromPhoto: (workoutId: Long) -> Unit,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val restEndsAt by viewModel.restEndsAt.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showFinish by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showStartPicker by rememberSaveable { mutableStateOf(false) }
    var editingSetId by rememberSaveable { mutableStateOf<Long?>(null) }

    AppScaffold(
        title = "Workout",
        actions = {
            IconButton(onClick = { onAddFromPhoto(state.workout.id) }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = "Add from rowing machine photo")
            }
            IconButton(onClick = { showDiscard = true }) {
                Icon(Icons.Filled.Delete, contentDescription = "Discard workout")
            }
            TextButton(onClick = { showFinish = true }) { Text("Finish") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            restEndsAt?.let { endsAt ->
                RestTimerBar(
                    endsAt = endsAt,
                    onAddTime = { viewModel.extendRest(15) },
                    onStop = viewModel::stopRest,
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Started ${formatDateTime(state.workout.startedAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { showStartPicker = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Change start time")
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
    if (showStartPicker) {
        DateTimePickerDialog(
            title = "Started",
            initial = state.workout.startedAt,
            max = Instant.now(),
            onConfirm = { viewModel.setStartTime(it); showStartPicker = false },
            onDismiss = { showStartPicker = false },
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
    if (showFinish) {
        FinishDialog(
            startedAt = state.workout.startedAt,
            initialNotes = state.workout.notes,
            hasSets = state.blocks.any { it.sets.isNotEmpty() },
            onConfirm = { notes, finishedAt -> showFinish = false; viewModel.finishWorkout(notes, finishedAt) },
            onDismiss = { showFinish = false },
        )
    }
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("All sets logged in this workout will be deleted.") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; viewModel.discardWorkout() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { showDiscard = false }) { Text("Cancel") } },
        )
    }
}

/**
 * Counts down to [endsAt]. It only ticks while the Log screen is visible; coming back after it
 * ran out just clears it, without the vibration.
 */
@Composable
private fun RestTimerBar(endsAt: Long, onAddTime: () -> Unit, onStop: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var remainingMs by remember(endsAt) { mutableLongStateOf(endsAt - SystemClock.elapsedRealtime()) }
    LaunchedEffect(endsAt) {
        while (true) {
            remainingMs = endsAt - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) break
            delay(200)
        }
        if (remainingMs > -1_000) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        onStop()
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null)
            val seconds = ((remainingMs.coerceAtLeast(0) + 999) / 1000).toInt()
            Text(
                "Rest ${SetInput.formatDuration(seconds)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            TextButton(onClick = onAddTime) { Text("+15 s") }
            TextButton(onClick = onStop) { Text("Skip") }
        }
    }
}

/** Asks for notes and the end time, which defaults to now (or start + 1 h for a backdated workout). */
@Composable
private fun FinishDialog(
    startedAt: Instant,
    initialNotes: String,
    hasSets: Boolean,
    onConfirm: (notes: String, finishedAt: Instant) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    var finishedAt by rememberSaveable { mutableStateOf(WorkoutDates.defaultEnd(startedAt, Instant.now())) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }

    if (pickingEnd) {
        DateTimePickerDialog(
            title = "Ended",
            initial = finishedAt,
            min = startedAt,
            max = Instant.now(),
            onConfirm = { finishedAt = it; pickingEnd = false },
            onDismiss = { pickingEnd = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish workout?") },
        text = {
            if (hasSets) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ended ${formatDateTime(finishedAt)}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { pickingEnd = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Change end time")
                        }
                    }
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Text("No sets were logged, so this workout won't be saved.")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(notes, finishedAt) }) { Text("Finish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

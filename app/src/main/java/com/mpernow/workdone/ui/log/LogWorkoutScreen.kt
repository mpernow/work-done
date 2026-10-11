package com.mpernow.workdone.ui.log

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.toValues
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.TrackingType
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

@Composable
fun LogWorkoutScreen(
    onCreateExercise: (String) -> Unit,
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
        is LogUiState.Active -> ActiveWorkout(current, viewModel, onCreateExercise)
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
        }
    }
}

@Composable
private fun ActiveWorkout(
    state: LogUiState.Active,
    viewModel: LogWorkoutViewModel,
    onCreateExercise: (String) -> Unit,
) {
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    val restEndsAt by viewModel.restEndsAt.collectAsStateWithLifecycle()
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showFinish by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val started = timeFormatter.format(state.workout.startedAt.atZone(ZoneId.systemDefault()))

    AppScaffold(
        title = "Workout",
        actions = {
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
                    Text(
                        "Started $started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.blocks, key = { it.exercise.id }) { block ->
                    ExerciseCard(
                        block = block,
                        unit = state.weightUnit,
                        onDraftChange = { viewModel.updateDraft(block.exercise.id, it) },
                        onAddSet = { viewModel.addSet(block.exercise.id) },
                        onDeleteSet = viewModel::deleteSet,
                    )
                }
                item {
                    OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text("Add exercise")
                    }
                }
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
    if (showFinish) {
        FinishDialog(
            initialNotes = state.workout.notes,
            hasSets = state.blocks.any { it.sets.isNotEmpty() },
            onConfirm = { notes -> showFinish = false; viewModel.finishWorkout(notes) },
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

@Composable
private fun ExerciseCard(
    block: ExerciseBlock,
    unit: WeightUnit,
    onDraftChange: (SetDraft) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (SetEntryEntity) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Text(block.exercise.name, style = MaterialTheme.typography.titleMedium)
            block.sets.forEachIndexed { index, set ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${index + 1}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(
                        SetInput.describe(set.toValues(), block.exercise.trackingType, unit),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onDeleteSet(set) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Delete set ${index + 1}")
                    }
                }
            }
            SetInputRow(
                type = block.exercise.trackingType,
                unit = unit,
                draft = block.draft,
                canAdd = block.canAddSet,
                onDraftChange = onDraftChange,
                onAdd = onAddSet,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SetInputRow(
    type: TrackingType,
    unit: WeightUnit,
    draft: SetDraft,
    canAdd: Boolean,
    onDraftChange: (SetDraft) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val field = Modifier.weight(1f)
        if (type == TrackingType.WEIGHT_REPS) {
            NumberField(draft.weight, { onDraftChange(draft.copy(weight = it)) }, unit.label, decimal = true, field)
        }
        if (type == TrackingType.DISTANCE_TIME) {
            NumberField(draft.distanceKm, { onDraftChange(draft.copy(distanceKm = it)) }, "km", decimal = true, field)
        }
        if (type == TrackingType.WEIGHT_REPS || type == TrackingType.REPS) {
            NumberField(draft.reps, { onDraftChange(draft.copy(reps = it)) }, "Reps", decimal = false, field)
        }
        if (type == TrackingType.DURATION || type == TrackingType.DISTANCE_TIME) {
            NumberField(draft.minutes, { onDraftChange(draft.copy(minutes = it)) }, "Min", decimal = false, field)
            NumberField(draft.seconds, { onDraftChange(draft.copy(seconds = it)) }, "Sec", decimal = false, field)
        }
        FilledTonalIconButton(onClick = onAdd, enabled = canAdd) {
            Icon(Icons.Filled.Check, contentDescription = "Log set")
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    decimal: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExercisePickerSheet(
    exercises: List<ExerciseEntity>,
    alreadyAdded: Set<Long>,
    onPick: (Long) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val trimmed = query.trim()
    val matches = exercises.filter { it.name.contains(trimmed, ignoreCase = true) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search exercises") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            if (matches.none { it.name.equals(trimmed, ignoreCase = true) }) {
                item(key = "create") {
                    ListItem(
                        headlineContent = { Text(if (trimmed.isEmpty()) "New exercise" else "Create “$trimmed”") },
                        leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
                        modifier = Modifier.clickable { onCreate(trimmed) },
                    )
                }
            }
            items(matches, key = { it.id }) { exercise ->
                ListItem(
                    headlineContent = { Text(exercise.name) },
                    supportingContent = { Text(exercise.category.label) },
                    trailingContent = {
                        if (exercise.id in alreadyAdded) Icon(Icons.Filled.Check, contentDescription = "In this workout")
                    },
                    modifier = Modifier.clickable { onPick(exercise.id) },
                )
            }
        }
    }
}

@Composable
private fun FinishDialog(
    initialNotes: String,
    hasSets: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var notes by rememberSaveable { mutableStateOf(initialNotes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Finish workout?") },
        text = {
            if (hasSets) {
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text("No sets were logged, so this workout won't be saved.")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(notes) }) { Text("Finish") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

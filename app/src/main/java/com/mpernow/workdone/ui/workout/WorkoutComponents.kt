package com.mpernow.workdone.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.toValues
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.SetValues
import com.mpernow.workdone.domain.TrackingType
import com.mpernow.workdone.domain.WeightUnit

/** An exercise's sets plus an input row for the next one. Tapping a set opens it for editing. */
@Composable
fun ExerciseCard(
    block: ExerciseBlock,
    unit: WeightUnit,
    onDraftChange: (SetDraft) -> Unit,
    onAddSet: () -> Unit,
    onEditSet: (SetEntryEntity) -> Unit,
    onDeleteSet: (SetEntryEntity) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Text(block.exercise.name, style = MaterialTheme.typography.titleMedium)
            block.sets.forEachIndexed { index, set ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onEditSet(set) },
                ) {
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
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SetFields(block.exercise.trackingType, unit, block.draft, onDraftChange)
                FilledTonalIconButton(onClick = onAddSet, enabled = block.canAddSet) {
                    Icon(Icons.Filled.Check, contentDescription = "Log set")
                }
            }
        }
    }
}

/** The input fields a tracking type needs, sharing the row's width equally. */
@Composable
fun RowScope.SetFields(
    type: TrackingType,
    unit: WeightUnit,
    draft: SetDraft,
    onDraftChange: (SetDraft) -> Unit,
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

@Composable
fun EditSetDialog(
    set: SetEntryEntity,
    exercise: ExerciseEntity,
    unit: WeightUnit,
    onSave: (SetValues) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(set.id) { mutableStateOf(SetInput.draftFrom(set.toValues(), unit)) }
    val values = SetInput.parse(draft, exercise.trackingType, unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit set") },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetFields(exercise.trackingType, unit, draft, onDraftChange = { draft = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { values?.let(onSave) }, enabled = values != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun AddExerciseButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
        Text("Add exercise")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisePickerSheet(
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
                    supportingContent = { Text(exercise.trackingType.label) },
                    trailingContent = {
                        if (exercise.id in alreadyAdded) Icon(Icons.Filled.Check, contentDescription = "In this workout")
                    },
                    modifier = Modifier.clickable { onPick(exercise.id) },
                )
            }
        }
    }
}

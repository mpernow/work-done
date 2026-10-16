package com.mpernow.workdone.ui.workout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.toValues
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.SetValues
import com.mpernow.workdone.domain.SplitsSummary
import com.mpernow.workdone.domain.TrackingType
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.domain.usesSplits

/**
 * An exercise's sets plus an input row for the next one, behind an "Add set" button once there are
 * sets. Tapping a set opens it for editing.
 */
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
            Text(
                block.number?.let { "${block.exercise.name} ($it)" } ?: block.exercise.name,
                style = MaterialTheme.typography.titleMedium,
            )
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
            if (block.exercise.trackingType.usesSplits) {
                SplitsSummary.of(block.sets.map { it.toValues() })?.let { summary ->
                    // Intervals leave out the rest, so their total is the time spent working.
                    val totalLabel = if (block.exercise.trackingType == TrackingType.DISTANCE_SPLITS) "Total" else "Work"
                    SplitsTotals(summary, totalLabel)
                }
            }
            // The input is always shown for the first set; after that it's opened with "Add set"
            // and closes again once the set is logged.
            var adding by rememberSaveable(block.sets.size) { mutableStateOf(false) }
            if (block.sets.isEmpty() || adding) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SetInputFields(block.exercise.trackingType, unit, block.draft, onDraftChange, Modifier.weight(1f))
                    FilledTonalIconButton(onClick = onAddSet, enabled = block.canAddSet) {
                        Icon(Icons.Filled.Check, contentDescription = "Log set")
                    }
                    if (adding) {
                        IconButton(onClick = { adding = false }) {
                            Icon(Icons.Filled.Close, contentDescription = "Cancel")
                        }
                    }
                }
            } else {
                TextButton(onClick = { adding = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Add set")
                }
            }
        }
    }
}

/** Totals and averages under the splits or intervals, in the same columns as the set rows. */
@Composable
private fun SplitsTotals(summary: SplitsSummary, totalLabel: String) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp, end = 48.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 4.dp))
        SummaryLine(totalLabel, summary.totalText)
        SummaryLine("Average", summary.averageText)
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

/** The input fields a tracking type needs, in one row, or two for intervals. */
@Composable
fun SetInputFields(
    type: TrackingType,
    unit: WeightUnit,
    draft: SetDraft,
    onDraftChange: (SetDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (type) {
            TrackingType.WEIGHT_REPS -> FieldRow {
                NumberField(draft.weight, { onDraftChange(draft.copy(weight = it)) }, unit.label, decimal = true, Modifier.weight(1f))
                NumberField(draft.reps, { onDraftChange(draft.copy(reps = it)) }, "Reps", decimal = false, Modifier.weight(1f))
            }
            TrackingType.REPS -> FieldRow {
                NumberField(draft.reps, { onDraftChange(draft.copy(reps = it)) }, "Reps", decimal = false, Modifier.weight(1f))
            }
            TrackingType.DURATION -> FieldRow {
                NumberField(draft.minutes, { onDraftChange(draft.copy(minutes = it)) }, "Min", decimal = false, Modifier.weight(1f))
                NumberField(draft.seconds, { onDraftChange(draft.copy(seconds = it)) }, "Sec", decimal = false, Modifier.weight(1f))
            }
            TrackingType.DISTANCE_TIME -> FieldRow {
                NumberField(draft.distanceKm, { onDraftChange(draft.copy(distanceKm = it)) }, "km", decimal = true, Modifier.weight(1f))
                NumberField(draft.minutes, { onDraftChange(draft.copy(minutes = it)) }, "Min", decimal = false, Modifier.weight(1f))
                NumberField(draft.seconds, { onDraftChange(draft.copy(seconds = it)) }, "Sec", decimal = false, Modifier.weight(1f))
            }
            TrackingType.INTERVALS_TIME, TrackingType.INTERVALS_DISTANCE, TrackingType.DISTANCE_SPLITS -> {
                FieldRow {
                    if (type == TrackingType.INTERVALS_TIME) {
                        DigitTimeField(draft.workTime, { onDraftChange(draft.copy(workTime = it)) }, "Time", DigitFormat.MIN_SEC, Modifier.weight(1f))
                    } else {
                        NumberField(draft.distanceMeters, { onDraftChange(draft.copy(distanceMeters = it)) }, "Meters", decimal = false, Modifier.weight(1f))
                    }
                    DigitTimeField(draft.split, { onDraftChange(draft.copy(split = it)) }, "Split", DigitFormat.SPLIT, Modifier.weight(1f))
                    NumberField(draft.strokeRate, { onDraftChange(draft.copy(strokeRate = it)) }, "s/m", decimal = false, Modifier.weight(1f))
                }
                FieldRow {
                    // A continuous piece has no rest between splits; keep the resistance field half width.
                    if (type != TrackingType.DISTANCE_SPLITS) {
                        DigitTimeField(draft.rest, { onDraftChange(draft.copy(rest = it)) }, "Rest", DigitFormat.MIN_SEC, Modifier.weight(1f))
                    }
                    NumberField(draft.resistance, { onDraftChange(draft.copy(resistance = it)) }, "Resistance", decimal = false, Modifier.weight(1f))
                    if (type == TrackingType.DISTANCE_SPLITS) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FieldRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
internal fun NumberField(
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

enum class DigitFormat(val placeholder: String, val maxDigits: Int) {
    MIN_SEC("m:ss", 4),
    SPLIT("m:ss.t", 4),
}

/** A time typed as digits only, with its separators added as you type; see [DigitTime]. */
@Composable
internal fun DigitTimeField(
    digits: String,
    onDigitsChange: (String) -> Unit,
    label: String,
    format: DigitFormat,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = digits,
        onValueChange = { typed -> onDigitsChange(typed.filter(Char::isDigit).take(format.maxDigits)) },
        label = { Text(label) },
        placeholder = { Text(format.placeholder) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        visualTransformation = DigitTimeTransformation(format),
        modifier = modifier,
    )
}

/** Saves a [SetDraft] in a Bundle as its fields, in declaration order. */
private val SetDraftSaver = listSaver<SetDraft, String>(
    save = {
        listOf(
            it.weight, it.reps, it.minutes, it.seconds, it.distanceKm, it.workTime,
            it.distanceMeters, it.split, it.rest, it.resistance, it.strokeRate,
        )
    },
    restore = { SetDraft(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9], it[10]) },
)

@Composable
fun EditSetDialog(
    set: SetEntryEntity,
    exercise: ExerciseEntity,
    unit: WeightUnit,
    onSave: (SetValues) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable(set.id, stateSaver = SetDraftSaver) {
        mutableStateOf(SetInput.draftFrom(set.toValues(), unit))
    }
    val values = SetInput.parse(draft, exercise.trackingType, unit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit set") },
        text = {
            SetInputFields(exercise.trackingType, unit, draft, onDraftChange = { draft = it })
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
                        // Picking it again adds another entry.
                        if (exercise.id in alreadyAdded) Icon(Icons.Filled.Check, contentDescription = "In this workout")
                    },
                    modifier = Modifier.clickable { onPick(exercise.id) },
                )
            }
        }
    }
}

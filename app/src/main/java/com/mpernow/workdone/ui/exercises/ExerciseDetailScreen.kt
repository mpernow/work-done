package com.mpernow.workdone.ui.exercises

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.mpernow.workdone.data.db.toValues
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.ExerciseSession
import com.mpernow.workdone.domain.MetricKind
import com.mpernow.workdone.domain.Progress
import com.mpernow.workdone.domain.ProgressMetric
import com.mpernow.workdone.domain.ProgressPoint
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.ui.charts.AxisSteps
import com.mpernow.workdone.ui.charts.ChartCard
import com.mpernow.workdone.ui.charts.ProgressLineChart
import com.mpernow.workdone.ui.charts.RangeChips
import com.mpernow.workdone.ui.charts.StatTile
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.PlaceholderScreen
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class ProgressRange(val label: String, val months: Long?) {
    THREE_MONTHS("3 months", 3),
    ONE_YEAR("1 year", 12),
    ALL("All time", null),
}

data class RecordUi(val label: String, val value: String, val date: LocalDate, val workoutId: Long)

data class SessionRow(val workoutId: Long, val date: LocalDate, val setCount: Int, val value: String)

data class ExerciseProgressUi(
    val exercise: ExerciseEntity,
    val hasSets: Boolean,
    val metrics: List<ProgressMetric>,
    val metric: ProgressMetric,
    val range: ProgressRange,
    val unit: WeightUnit,
    /** The selected metric within the range, oldest first, in display units. */
    val points: List<ProgressPoint>,
    /** All-time bests, one per metric. */
    val records: List<RecordUi>,
    /** Workouts within the range, newest first. */
    val sessions: List<SessionRow>,
)

class ExerciseDetailViewModel(
    exerciseRepository: ExerciseRepository,
    workoutRepository: WorkoutRepository,
    settingsRepository: SettingsRepository,
    exerciseId: Long,
) : ViewModel() {
    private val selectedMetric = MutableStateFlow<ProgressMetric?>(null)
    private val range = MutableStateFlow(ProgressRange.ALL)

    val uiState: StateFlow<ExerciseProgressUi?> = combine(
        exerciseRepository.observe(exerciseId),
        workoutRepository.observeFinishedSetsForExercise(exerciseId),
        settingsRepository.weightUnit,
        selectedMetric,
        range,
    ) { exercise, sets, unit, selected, range ->
        if (exercise == null) return@combine null
        val zone = ZoneId.systemDefault()
        val sessions = sets.groupBy { it.set.workoutId }.map { (workoutId, workoutSets) ->
            ExerciseSession(
                workoutId = workoutId,
                date = workoutSets.first().startedAt.atZone(zone).toLocalDate(),
                sets = workoutSets.map { it.set.toValues() },
            )
        }
        val metrics = Progress.metricsFor(exercise.trackingType)
        val metric = selected?.takeIf { it in metrics } ?: metrics.first()
        val cutoff = range.months?.let { LocalDate.now(zone).minusMonths(it) }
        val inRange = sessions.filter { cutoff == null || !it.date.isBefore(cutoff) }

        fun display(value: Double, metric: ProgressMetric) =
            Progress.format(Progress.toDisplay(value, metric.kind, unit), metric.kind, unit)

        ExerciseProgressUi(
            exercise = exercise,
            hasSets = sessions.isNotEmpty(),
            metrics = metrics,
            metric = metric,
            range = range,
            unit = unit,
            points = Progress.points(inRange, metric).map { it.copy(value = Progress.toDisplay(it.value, metric.kind, unit)) },
            records = metrics.mapNotNull { m ->
                val label = m.recordLabel ?: return@mapNotNull null
                Progress.best(Progress.points(sessions, m), m)?.let { RecordUi(label, display(it.value, m), it.date, it.workoutId) }
            },
            sessions = inRange.asReversed().map { session ->
                SessionRow(
                    workoutId = session.workoutId,
                    date = session.date,
                    setCount = session.sets.size,
                    value = Progress.sessionValue(session.sets, metric)?.let { display(it, metric) } ?: "–",
                )
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectMetric(metric: ProgressMetric) {
        selectedMetric.value = metric
    }

    fun selectRange(range: ProgressRange) {
        this.range.value = range
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM")
private val longDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun ExerciseDetailScreen(
    exerciseId: Long,
    onNavigateUp: () -> Unit,
    onEdit: () -> Unit,
    onWorkoutClick: (Long) -> Unit,
    viewModel: ExerciseDetailViewModel = viewModel(
        factory = appViewModelFactory {
            ExerciseDetailViewModel(it.exerciseRepository, it.workoutRepository, it.settingsRepository, exerciseId)
        },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppScaffold(
        title = state?.exercise?.name.orEmpty(),
        onNavigateUp = onNavigateUp,
        actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit exercise") }
        },
    ) { padding ->
        val ui = state ?: return@AppScaffold
        if (!ui.hasSets) {
            PlaceholderScreen(
                "No progress yet",
                "Finish a workout with this exercise to see charts and records.",
                Modifier.padding(padding),
            )
            return@AppScaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RangeChips(ProgressRange.entries, ui.range, { it.label }, viewModel::selectRange)
            }
            item { ProgressChartCard(ui, viewModel::selectMetric) }
            item { SectionTitle("Personal records") }
            item { RecordGrid(ui.records, onWorkoutClick) }
            item { SectionTitle("Workouts") }
            items(ui.sessions, key = { it.workoutId }) { session ->
                ListItem(
                    headlineContent = { Text(longDate.format(session.date)) },
                    supportingContent = { Text(if (session.setCount == 1) "1 set" else "${session.setCount} sets") },
                    trailingContent = { Text(session.value, style = MaterialTheme.typography.bodyLarge) },
                    modifier = Modifier.clickable { onWorkoutClick(session.workoutId) },
                )
            }
        }
    }
}

@Composable
private fun ProgressChartCard(ui: ExerciseProgressUi, onSelectMetric: (ProgressMetric) -> Unit) {
    val metric = ui.metric
    val latest = ui.points.lastOrNull()
    ChartCard(
        title = Progress.axisUnit(metric.kind, ui.unit)?.let { "${metric.label} ($it)" } ?: metric.label,
        // The latest value is the one direct label; press and hold the chart for the others.
        subtitle = latest?.let { "Latest ${Progress.format(it.value, metric.kind, ui.unit)} · ${shortDate.format(it.date)}" },
        controls = {
            if (ui.metrics.size > 1) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ui.metrics.forEachIndexed { index, option ->
                        SegmentedButton(
                            selected = option == metric,
                            onClick = { onSelectMetric(option) },
                            shape = SegmentedButtonDefaults.itemShape(index, ui.metrics.size),
                        ) { Text(option.label, maxLines = 1) }
                    }
                }
            }
        },
    ) {
        if (ui.points.isEmpty()) {
            Text(
                "No workouts in this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val points = ui.points
            ProgressLineChart(
                xs = points.map { it.date.toEpochDay() },
                ys = points.map { it.value },
                xLabel = { shortDate.format(LocalDate.ofEpochDay(it)) },
                yLabel = { Progress.formatAxis(it, metric.kind) },
                tooltip = { index ->
                    val point = points[index]
                    "${shortDate.format(point.date)} · ${Progress.format(point.value, metric.kind, ui.unit)}"
                },
                axisSteps = when (metric.kind) {
                    MetricKind.DURATION, MetricKind.PACE, MetricKind.SPLIT -> AxisSteps.TIME
                    MetricKind.REPS, MetricKind.STROKE_RATE -> AxisSteps.INTEGER
                    MetricKind.WEIGHT, MetricKind.DISTANCE -> AxisSteps.DECIMAL
                },
                surfaceColor = CardDefaults.cardColors().containerColor,
            )
        }
    }
}

@Composable
private fun RecordGrid(records: List<RecordUi>, onWorkoutClick: (Long) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        records.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { record ->
                    StatTile(
                        label = record.label,
                        value = record.value,
                        supporting = shortDate.format(record.date),
                        modifier = Modifier.weight(1f).clickable { onWorkoutClick(record.workoutId) },
                    )
                }
                // Keep a lone last tile at half width.
                if (pair.size == 1) Column(Modifier.weight(1f)) {}
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
}

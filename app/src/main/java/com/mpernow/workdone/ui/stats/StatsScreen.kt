package com.mpernow.workdone.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.MetricKind
import com.mpernow.workdone.domain.Progress
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.StatsRange
import com.mpernow.workdone.domain.TrainingStats
import com.mpernow.workdone.domain.WeekTotals
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.domain.WorkoutTotals
import com.mpernow.workdone.ui.charts.AxisSteps
import com.mpernow.workdone.ui.charts.ChartCard
import com.mpernow.workdone.ui.charts.ChartTable
import com.mpernow.workdone.ui.charts.ConsistencyHeatmap
import com.mpernow.workdone.ui.charts.RangeChips
import com.mpernow.workdone.ui.charts.StatTile
import com.mpernow.workdone.ui.charts.WeeklyColumnChart
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

data class StatsUi(
    val hasData: Boolean,
    val range: StatsRange,
    val unit: WeightUnit,
    val today: LocalDate,
    val weekStreak: Int,
    val workouts: Int,
    val weeks: List<WeekTotals>,
    /** Weekly volume in the display unit. */
    val weeklyVolume: List<Double>,
    val setsPerDay: Map<LocalDate, Int>,
)

class StatsViewModel(workoutRepository: WorkoutRepository, settingsRepository: SettingsRepository) : ViewModel() {
    private val range = MutableStateFlow(StatsRange.THREE_MONTHS)

    val uiState: StateFlow<StatsUi?> = combine(
        workoutRepository.observeWorkoutTotals(),
        settingsRepository.weightUnit,
        range,
    ) { totals, unit, range ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rows = totals.map {
            WorkoutTotals(it.workoutId, it.startedAt.atZone(zone).toLocalDate(), it.setCount, it.volumeKg)
        }
        val weeks = TrainingStats.weeklyTotals(rows, today, range.weeks)
        val from = weeks.first().weekStart
        StatsUi(
            hasData = rows.isNotEmpty(),
            range = range,
            unit = unit,
            today = today,
            weekStreak = TrainingStats.weekStreak(rows.map { it.date }, today),
            workouts = weeks.sumOf { it.workouts },
            weeks = weeks,
            weeklyVolume = weeks.map { SetInput.fromKg(it.volumeKg, unit) },
            setsPerDay = TrainingStats.setsPerDay(rows, from),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectRange(range: StatsRange) {
        this.range.value = range
    }
}

private val weekFormatter = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun StatsScreen(
    viewModel: StatsViewModel = viewModel(
        factory = appViewModelFactory { StatsViewModel(it.workoutRepository, it.settingsRepository) },
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    AppScaffold(title = "Statistics") { padding ->
        val ui = state ?: return@AppScaffold
        if (!ui.hasData) {
            PlaceholderScreen("No statistics yet", "Finish a workout to start building them.", Modifier.padding(padding))
            return@AppScaffold
        }
        val weekLabels = ui.weeks.map { weekFormatter.format(it.weekStart) }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { RangeChips(StatsRange.entries, ui.range, { it.label }, viewModel::selectRange) }
            item { KpiRow(ui) }
            item { WorkoutsPerWeek(ui, weekLabels) }
            item { VolumePerWeek(ui, weekLabels) }
            item {
                ChartCard(title = "Consistency", subtitle = "Sets per day") {
                    ConsistencyHeatmap(
                        setsPerDay = ui.setsPerDay,
                        firstWeekStart = ui.weeks.first().weekStart,
                        weeks = ui.weeks.size,
                        today = ui.today,
                        surfaceColor = CardDefaults.cardColors().containerColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiRow(ui: StatsUi) {
    val perWeek = ui.workouts.toDouble() / ui.weeks.size
    // Equal-height tiles even when only some have a supporting line.
    Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val tile = Modifier.weight(1f).fillMaxHeight()
        StatTile("Week streak", "${ui.weekStreak}", tile, supporting = if (ui.weekStreak == 1) "week" else "weeks")
        StatTile("Workouts", "${ui.workouts}", tile)
        StatTile("Per week", SetInput.formatDecimal(Math.round(perWeek * 10) / 10.0), tile)
    }
}

@Composable
private fun WorkoutsPerWeek(ui: StatsUi, weekLabels: List<String>) {
    val counts = ui.weeks.map { it.workouts }
    ChartCard(
        title = "Workouts per week",
        table = ChartTable("Week of", "Workouts", weekLabels.zip(counts.map { "$it" })),
    ) {
        WeeklyColumnChart(
            values = counts.map { it.toDouble() },
            axisSteps = AxisSteps.INTEGER,
            xLabel = { weekLabels.getOrElse(it) { "" } },
            yLabel = { "${it.toInt()}" },
            tooltip = { index -> "Week of ${weekLabels[index]} · ${counts[index]}" },
        )
    }
}

@Composable
private fun VolumePerWeek(ui: StatsUi, weekLabels: List<String>) {
    val unit = ui.unit.label
    val volumes = ui.weeklyVolume
    ChartCard(
        title = "Volume per week ($unit)",
        subtitle = "Weight × reps, from weighted exercises",
        table = ChartTable("Week of", "Volume", weekLabels.zip(volumes.map { "${Progress.compactNumber(it)} $unit" })),
    ) {
        if (volumes.all { it == 0.0 }) {
            EmptyChartText()
        } else {
            WeeklyColumnChart(
                values = volumes,
                axisSteps = AxisSteps.DECIMAL,
                xLabel = { weekLabels.getOrElse(it) { "" } },
                yLabel = { Progress.formatAxis(it, MetricKind.WEIGHT) },
                tooltip = { index -> "Week of ${weekLabels[index]} · ${Progress.compactNumber(volumes[index])} $unit" },
            )
        }
    }
}

@Composable
private fun EmptyChartText() {
    Text(
        "Nothing logged in this period.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

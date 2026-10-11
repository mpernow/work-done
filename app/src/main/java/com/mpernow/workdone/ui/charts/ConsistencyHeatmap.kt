package com.mpernow.workdone.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mpernow.workdone.domain.TrainingStats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFormatter = DateTimeFormatter.ofPattern("EEE d MMM")

/** Shades for heat levels 1–4: one hue, light to dark, as alpha steps of the primary colour. */
private val LEVEL_ALPHAS = floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f)

/**
 * One column per week, one row per weekday (first day of the week at the top), shaded by
 * the number of sets done that day. Tap a day to see its count.
 */
@Composable
fun ConsistencyHeatmap(
    setsPerDay: Map<LocalDate, Int>,
    firstWeekStart: LocalDate,
    weeks: Int,
    today: LocalDate,
    surfaceColor: Color,
) {
    val colors = MaterialTheme.colorScheme
    val levelColors = LEVEL_ALPHAS.mapIndexed { level, alpha ->
        // Rest days get a faint neutral step so the grid is visible; training days shade the primary colour.
        if (level == 0) colors.onSurface.copy(alpha = 0.08f).compositeOver(surfaceColor)
        else colors.primary.copy(alpha = alpha).compositeOver(surfaceColor)
    }
    var selected by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val trainingDays = setsPerDay.count { it.value > 0 }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BoxWithConstraints {
            val gap = 2.dp
            val cell = min((maxWidth - gap * (weeks - 1)) / weeks, 28.dp)
            val step = cell + gap
            Canvas(
                Modifier
                    .size(width = step * weeks - gap, height = step * 7 - gap)
                    .semantics { contentDescription = "$trainingDays training days in the last $weeks weeks" }
                    .pointerInput(weeks, firstWeekStart) {
                        detectTapGestures { offset ->
                            val column = (offset.x / step.toPx()).toInt()
                            val row = (offset.y / step.toPx()).toInt()
                            val date = firstWeekStart.plusDays(column * 7L + row)
                            selected = if (column in 0 until weeks && row in 0..6 && !date.isAfter(today)) date else null
                        }
                    },
            ) {
                val cellPx = cell.toPx()
                val stepPx = step.toPx()
                val radius = CornerRadius(2.dp.toPx())
                for (column in 0 until weeks) {
                    for (row in 0..6) {
                        val date = firstWeekStart.plusDays(column * 7L + row)
                        if (date.isAfter(today)) continue
                        val topLeft = Offset(column * stepPx, row * stepPx)
                        val level = TrainingStats.heatLevel(setsPerDay[date] ?: 0)
                        drawRoundRect(levelColors[level], topLeft, Size(cellPx, cellPx), radius)
                        if (date == selected) {
                            drawRoundRect(colors.onSurface, topLeft, Size(cellPx, cellPx), radius, style = Stroke(1.5.dp.toPx()))
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            val day = selected
            Text(
                if (day == null) "Tap a day to see its sets" else "${dayFormatter.format(day)} · ${setsLabel(setsPerDay[day] ?: 0)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            HeatLegend(levelColors)
        }
    }
}

private fun setsLabel(sets: Int) = when (sets) {
    0 -> "rest day"
    1 -> "1 set"
    else -> "$sets sets"
}

@Composable
private fun HeatLegend(levelColors: List<Color>) {
    val labelStyle = MaterialTheme.typography.labelSmall
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Less", style = labelStyle, color = labelColor)
        levelColors.forEach { color ->
            Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        }
        Text("More", style = labelStyle, color = labelColor)
    }
}

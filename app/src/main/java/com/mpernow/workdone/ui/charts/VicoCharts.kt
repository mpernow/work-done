package com.mpernow.workdone.ui.charts

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patrykandpatrick.vico.compose.cartesian.CartesianChartHost
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.axis.HorizontalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.VerticalAxis
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisGuidelineComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLabelComponent
import com.patrykandpatrick.vico.compose.cartesian.axis.rememberAxisLineComponent
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianLayerRangeProvider
import com.patrykandpatrick.vico.compose.cartesian.data.CartesianValueFormatter
import com.patrykandpatrick.vico.compose.cartesian.data.columnModel
import com.patrykandpatrick.vico.compose.cartesian.data.lineModel
import com.patrykandpatrick.vico.compose.cartesian.layer.CartesianLayerPadding
import com.patrykandpatrick.vico.compose.cartesian.layer.ColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.LineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberColumnCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLine
import com.patrykandpatrick.vico.compose.cartesian.layer.rememberLineCartesianLayer
import com.patrykandpatrick.vico.compose.cartesian.marker.CartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.DefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.marker.rememberDefaultCartesianMarker
import com.patrykandpatrick.vico.compose.cartesian.rememberCartesianChart
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import com.patrykandpatrick.vico.compose.common.Fill
import com.patrykandpatrick.vico.compose.common.Insets
import com.patrykandpatrick.vico.compose.common.ProvideVicoTheme
import com.patrykandpatrick.vico.compose.common.component.rememberLineComponent
import com.patrykandpatrick.vico.compose.common.component.rememberShapeComponent
import com.patrykandpatrick.vico.compose.common.component.rememberTextComponent
import com.patrykandpatrick.vico.compose.common.data.ExtraStore
import com.patrykandpatrick.vico.compose.m3.common.rememberM3VicoTheme
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** How many x steps apart the bottom-axis labels are, so roughly four fit. */
private val LabelSpacingKey = ExtraStore.Key<Int>()

private fun labelSpacing(xRange: Double): Int = ceil(xRange / 3.0).toInt().coerceAtLeast(1)

/** The y-axis tick step, so ticks land on round numbers. */
private val YStepKey = ExtraStore.Key<Double>()

/** What kind of numbers an axis shows, which decides what counts as a round tick step. */
enum class AxisSteps { DECIMAL, INTEGER, TIME }

private val TIME_STEPS = listOf(1.0, 5.0, 10.0, 15.0, 30.0, 60.0, 120.0, 300.0, 600.0, 900.0, 1800.0, 3600.0)

/**
 * A round step giving about four ticks across [span]: 1, 2, 2.5 or 5 × 10ⁿ; whole numbers for
 * [AxisSteps.INTEGER]; and clock-friendly seconds (30 s, 1 min, 5 min…) for [AxisSteps.TIME].
 */
internal fun niceStep(span: Double, steps: AxisSteps): Double {
    val raw = span / 4
    if (steps == AxisSteps.TIME) return TIME_STEPS.firstOrNull { it >= raw } ?: (ceil(raw / 3600) * 3600)
    val magnitude = 10.0.pow(floor(log10(raw.coerceAtLeast(1e-9))))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    return if (steps == AxisSteps.INTEGER) ceil(step).coerceAtLeast(1.0) else step
}

/** Columns start at zero and the top is rounded up to the next tick. */
private object ZeroBasedSteppedYRange : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double = 0.0

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double {
        val step = extraStore.getOrNull(YStepKey) ?: return maxY
        return (ceil(maxY / step) * step).coerceAtLeast(step)
    }
}

/** The y range and tick step for a progress line, computed with the data and read back while drawing. */
private val LineYRangeKey = ExtraStore.Key<Triple<Double, Double, Double>>()

/**
 * Pads the data's range so the line stays off the floor and ceiling (instead of starting the
 * axis at zero, which would flatten progress), then widens it to whole tick steps.
 */
internal fun steppedLineRange(minY: Double, maxY: Double, steps: AxisSteps): Triple<Double, Double, Double> {
    val padding = if (maxY > minY) (maxY - minY) * 0.15 else (abs(maxY) * 0.1).coerceAtLeast(1.0)
    val low = (minY - padding).coerceAtLeast(0.0)
    val high = maxY + padding
    val step = niceStep(high - low, steps)
    return Triple(floor(low / step) * step, ceil(high / step) * step, step)
}

private object SteppedLineYRange : CartesianLayerRangeProvider {
    override fun getMinY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        extraStore.getOrNull(LineYRangeKey)?.first ?: minY

    override fun getMaxY(minY: Double, maxY: Double, extraStore: ExtraStore): Double =
        extraStore.getOrNull(LineYRangeKey)?.second ?: maxY
}

/** Shared chrome: theme colours, solid hairline gridlines, and a press-and-hold tooltip. */
@Composable
private fun ChartTheme(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    ProvideVicoTheme(
        rememberM3VicoTheme(lineColor = colors.outlineVariant, textColor = colors.onSurfaceVariant),
        content,
    )
}

@Composable
private fun rememberGuideline() = rememberAxisGuidelineComponent(
    fill = Fill(MaterialTheme.colorScheme.outlineVariant),
    thickness = 1.dp,
    shape = RectangleShape,
)

@Composable
private fun rememberTooltip(text: (x: Double) -> String): CartesianMarker {
    val colors = MaterialTheme.colorScheme
    val currentText by rememberUpdatedState(text)
    val label = rememberTextComponent(
        style = TextStyle(color = colors.inverseOnSurface, fontSize = 12.sp),
        padding = Insets(horizontal = 8.dp, vertical = 4.dp),
        background = rememberShapeComponent(Fill(colors.inverseSurface), RoundedCornerShape(4.dp)),
    )
    return rememberDefaultCartesianMarker(
        label = label,
        valueFormatter = remember { DefaultCartesianMarker.ValueFormatter { _, targets -> currentText(targets.first().x) } },
        guideline = rememberAxisLineComponent(fill = Fill(colors.outline), thickness = 1.dp),
    )
}

/**
 * One series over time. [xs] are days (epoch days), so gaps between workouts show as gaps;
 * the whole range is fitted to the width. Press and hold to read a point.
 */
@Composable
fun ProgressLineChart(
    xs: List<Long>,
    ys: List<Double>,
    xLabel: (Long) -> String,
    yLabel: (Double) -> String,
    tooltip: (index: Int) -> String,
    axisSteps: AxisSteps,
    surfaceColor: Color,
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(xs, ys) {
        producer.runTransaction {
            lineModel { series(xs, ys) }
            extras {
                it[LabelSpacingKey] = labelSpacing((xs.last() - xs.first()).toDouble())
                it[LineYRangeKey] = steppedLineRange(ys.min(), ys.max(), axisSteps)
            }
        }
    }
    val currentXLabel by rememberUpdatedState(xLabel)
    val currentYLabel by rememberUpdatedState(yLabel)
    val color = MaterialTheme.colorScheme.primary
    ChartTheme {
        val line = LineCartesianLayer.rememberLine(
            fill = remember(color) { LineCartesianLayer.LineFill.single(Fill(color)) },
            stroke = LineCartesianLayer.LineStroke.Continuous(thickness = 2.dp, cap = StrokeCap.Round),
            areaFill = remember(color) { LineCartesianLayer.AreaFill.single(Fill(color.copy(alpha = 0.10f))) },
            pointProvider = LineCartesianLayer.PointProvider.single(
                LineCartesianLayer.Point(
                    // 8 dp dot with a 2 dp ring in the card's colour, so it stays legible on the line.
                    rememberShapeComponent(Fill(color), CircleShape, strokeFill = Fill(surfaceColor), strokeThickness = 2.dp),
                    size = 12.dp,
                ),
            ),
        )
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberLineCartesianLayer(
                    lineProvider = LineCartesianLayer.LineProvider.series(line),
                    rangeProvider = SteppedLineYRange,
                ),
                startAxis = VerticalAxis.rememberStart(
                    line = null,
                    label = rememberAxisLabelComponent(),
                    valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentYLabel(value) } },
                    guideline = rememberGuideline(),
                    tick = null,
                    itemPlacer = remember { VerticalAxis.ItemPlacer.step(step = { it.getOrNull(LineYRangeKey)?.third }) },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentXLabel(value.toLong()) } },
                    guideline = null,
                    itemPlacer = remember {
                        HorizontalAxis.ItemPlacer.aligned(spacing = { it.getOrNull(LabelSpacingKey) ?: 1 })
                    },
                ),
                marker = rememberTooltip { x -> tooltip(xs.indexOf(x.toLong()).coerceAtLeast(0)) },
                // Room for the first and last dots, which would otherwise be cut in half at the edges.
                layerPadding = { CartesianLayerPadding(unscalableStart = 8.dp, unscalableEnd = 8.dp) },
                getXStep = { _, _, _ -> 1.0 },
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth(),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

/** One column per week, oldest on the left. Press and hold a column to read it. */
@Composable
fun WeeklyColumnChart(
    values: List<Double>,
    axisSteps: AxisSteps,
    xLabel: (index: Int) -> String,
    yLabel: (Double) -> String,
    tooltip: (index: Int) -> String,
    modifier: Modifier = Modifier,
) {
    val producer = remember { CartesianChartModelProducer() }
    LaunchedEffect(values) {
        producer.runTransaction {
            columnModel { series(values) }
            extras {
                it[LabelSpacingKey] = labelSpacing((values.size - 1).toDouble())
                it[YStepKey] = niceStep(values.max(), axisSteps)
            }
        }
    }
    val currentXLabel by rememberUpdatedState(xLabel)
    val currentYLabel by rememberUpdatedState(yLabel)
    val color = MaterialTheme.colorScheme.primary
    ChartTheme {
        CartesianChartHost(
            chart = rememberCartesianChart(
                rememberColumnCartesianLayer(
                    columnProvider = ColumnCartesianLayer.ColumnProvider.series(
                        // Rounded at the data end, square at the baseline.
                        rememberLineComponent(Fill(color), thickness = 16.dp, shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    ),
                    columnCollectionSpacing = 6.dp,
                    rangeProvider = ZeroBasedSteppedYRange,
                ),
                startAxis = VerticalAxis.rememberStart(
                    line = null,
                    valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentYLabel(value) } },
                    guideline = rememberGuideline(),
                    tick = null,
                    itemPlacer = remember { VerticalAxis.ItemPlacer.step(step = { it.getOrNull(YStepKey) }) },
                ),
                bottomAxis = HorizontalAxis.rememberBottom(
                    valueFormatter = remember { CartesianValueFormatter { _, value, _ -> currentXLabel(value.toInt()) } },
                    guideline = null,
                    itemPlacer = remember {
                        HorizontalAxis.ItemPlacer.aligned(spacing = { it.getOrNull(LabelSpacingKey) ?: 1 })
                    },
                ),
                marker = rememberTooltip { x -> tooltip(x.toInt()) },
            ),
            modelProducer = producer,
            modifier = modifier.fillMaxWidth(),
            scrollState = rememberVicoScrollState(scrollEnabled = false),
            zoomState = rememberVicoZoomState(zoomEnabled = false, initialZoom = Zoom.Content),
        )
    }
}

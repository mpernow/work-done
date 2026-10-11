package com.mpernow.workdone.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The same data as a chart, as label/value rows, for when a chart is hard to read. */
data class ChartTable(val labelHeader: String, val valueHeader: String, val rows: List<Pair<String, String>>)

/**
 * A titled card for one chart. With a [table], a toggle in the header switches between the
 * chart and the table view of the same data.
 */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    table: ChartTable? = null,
    controls: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    var showTable by rememberSaveable(title) { mutableStateOf(false) }
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (subtitle != null) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (table != null) {
                    TextButton(onClick = { showTable = !showTable }) { Text(if (showTable) "Chart" else "Table") }
                }
            }
            controls()
            if (showTable && table != null) TableView(table) else content()
        }
    }
}

@Composable
private fun TableView(table: ChartTable) {
    Column {
        TableRow(table.labelHeader, table.valueHeader, header = true)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        // Newest first, which is what you usually look for.
        table.rows.asReversed().forEach { (label, value) -> TableRow(label, value, header = false) }
    }
}

@Composable
private fun TableRow(label: String, value: String, header: Boolean) {
    val style = if (header) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium
    val color = if (header) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = style, color = color, modifier = Modifier.weight(1f))
        Text(value, style = style, color = color, textAlign = TextAlign.End)
    }
}

/** A headline number: label above, value below. */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, supporting: String? = null) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (supporting != null) {
                Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One row of chips choosing the period that everything below it covers. */
@Composable
fun <T> RangeChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        options.forEach { option ->
            FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
        }
    }
}

/** Horizontal bars in a single colour, each labelled with its value at the bar's end. */
@Composable
fun HorizontalBars(rows: List<Pair<String, Int>>, valueLabel: (Int) -> String) {
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: return
    val barColor = MaterialTheme.colorScheme.primary
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (label, value) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp))
                BoxWithConstraints(Modifier.weight(1f)) {
                    // Leave room after the longest bar for its value label.
                    val barWidth = (maxWidth - 64.dp) * (value.toFloat() / max)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier
                                .width(barWidth.coerceAtLeast(2.dp))
                                .height(12.dp)
                                .background(barColor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)),
                        ) {}
                        Text(
                            valueLabel(value),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

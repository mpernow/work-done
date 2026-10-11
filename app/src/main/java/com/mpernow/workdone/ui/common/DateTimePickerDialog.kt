package com.mpernow.workdone.ui.common

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val limitFormatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

/**
 * Picks a date, then a time. The result must lie within [min]..[max]; days outside that range
 * can't be selected and an out-of-range time disables OK.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(
    title: String,
    initial: Instant,
    onConfirm: (Instant) -> Unit,
    onDismiss: () -> Unit,
    min: Instant? = null,
    max: Instant? = null,
) {
    val zone = ZoneId.systemDefault()
    val initialLocal = initial.atZone(zone)
    val minDate = min?.atZone(zone)?.toLocalDate()
    val maxDate = max?.atZone(zone)?.toLocalDate()
    var pickingTime by rememberSaveable { mutableStateOf(false) }

    // The date picker works in UTC midnights, regardless of the device's time zone.
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = initialLocal.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return (minDate == null || !date.isBefore(minDate)) && (maxDate == null || !date.isAfter(maxDate))
            }

            override fun isSelectableYear(year: Int): Boolean =
                (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
        },
    )
    val timeState = rememberTimePickerState(
        initialHour = initialLocal.hour,
        initialMinute = initialLocal.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )

    if (!pickingTime) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickingTime = true }, enabled = dateState.selectedDateMillis != null) {
                    Text("Next")
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        ) {
            DatePicker(dateState)
        }
    } else {
        val date = dateState.selectedDateMillis
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
            ?: LocalDate.now(zone)
        val result = date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant()
        val error = when {
            max != null && result.isAfter(max) -> "Must be no later than ${limitFormatter.format(max.atZone(zone))}"
            min != null && result.isBefore(min) -> "Must be no earlier than ${limitFormatter.format(min.atZone(zone))}"
            else -> null
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column {
                    TimePicker(timeState)
                    if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = { onConfirm(result) }, enabled = error == null) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingTime = false }) { Text("Back") } },
        )
    }
}

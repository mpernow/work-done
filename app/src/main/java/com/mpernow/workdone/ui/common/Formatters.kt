package com.mpernow.workdone.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val dateWithYearFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

fun formatTime(instant: Instant): String = timeFormatter.format(instant.atZone(ZoneId.systemDefault()))

/** "Thu 9 Oct", with the year added when it isn't the current one. */
fun formatDate(instant: Instant): String {
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    val formatter = if (date.year == LocalDate.now().year) dateFormatter else dateWithYearFormatter
    return formatter.format(date)
}

/** Just the time for today, otherwise "Thu 9 Oct, 14:05". */
fun formatDateTime(instant: Instant): String {
    val date = instant.atZone(ZoneId.systemDefault()).toLocalDate()
    return if (date == LocalDate.now()) formatTime(instant) else "${formatDate(instant)}, ${formatTime(instant)}"
}

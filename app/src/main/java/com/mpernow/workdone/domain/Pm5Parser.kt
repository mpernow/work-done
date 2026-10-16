package com.mpernow.workdone.domain

import java.time.LocalDate
import java.time.Month
import kotlin.math.abs
import kotlin.math.roundToInt

/** A word recognised in a photo, with its bounding box in pixels. */
data class OcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = bottom - top
}

/** The workout named at the top of a PM5 results screen, such as "6x500m/2:00r", "2000m" or "10:00". */
sealed interface Pm5Workout {
    data class DistanceIntervals(val count: Int, val meters: Int, val restSeconds: Int) : Pm5Workout
    data class TimeIntervals(val count: Int, val workSeconds: Int, val restSeconds: Int) : Pm5Workout
    data class SingleDistance(val meters: Int) : Pm5Workout
    data class SingleTime(val seconds: Int) : Pm5Workout
}

/** One row of the results table. Distances and times are per row, already converted from running totals. */
data class Pm5Row(
    val timeSeconds: Double?,
    val meters: Int?,
    val splitSeconds: Double?,
    val strokeRate: Int?,
    /** What looks wrong with this row, for the user to check; null if it's consistent. */
    val problem: String? = null,
)

data class Pm5Result(
    val workout: Pm5Workout?,
    val date: LocalDate?,
    /** The summary row above the splits: total time, distance, average split and rate. */
    val total: Pm5Row?,
    val rows: List<Pm5Row>,
    /** Problems with the screen as a whole, such as rows that don't add up to the total. */
    val problems: List<String>,
)

/**
 * Reads a Concept2 PM5 "View Detail" results screen from recognised words.
 *
 * The screen has a fixed layout: the workout description and date at the top, a column header
 * (time · meter · /500m · s/m), a summary row, then one row per interval or split. Words are
 * grouped into lines by their height on the photo, so it doesn't matter how the recogniser
 * splits the screen into blocks. The screen repeats itself (times add up to the total, each
 * time matches its split), which is used to flag misread numbers.
 */
object Pm5Parser {
    fun parse(words: List<OcrWord>): Pm5Result {
        val lines = groupIntoLines(withoutOutliers(words))
        val lineTexts = lines.map { line -> line.joinToString(" ") { normalizeToken(it.text) } }
        val workout = lineTexts.firstNotNullOfOrNull(::parseWorkout)
        val date = lineTexts.firstNotNullOfOrNull(::parseDate)

        // The table starts after the column header; without one, try every line.
        val headerIndex = lineTexts.indexOfFirst { it.lowercase().let { t -> "meter" in t || "s/m" in t } }
        val columns = lines.getOrNull(headerIndex)?.let(::findColumns) ?: Columns()
        val tableRows = lines.drop(headerIndex + 1).mapNotNull { parseRow(it, columns) }

        val (total, splitRows) = separateTotal(tableRows, workout)
        val rows = toPerRowTimes(toPerRowDistances(splitRows, total, workout), total, workout).map(::checkRow)
        return Pm5Result(workout, date, total, rows, overallProblems(workout, total, rows))
    }

    /**
     * Drops words much taller than the rest, which come from the monitor's buttons and frame
     * rather than its text, and would otherwise join several table rows.
     */
    private fun withoutOutliers(words: List<OcrWord>): List<OcrWord> {
        if (words.isEmpty()) return words
        val median = words.map { it.height }.sorted()[words.size / 2]
        return words.filter { it.height <= median * 2 }
    }

    /** Horizontal centres of the table's columns, from the header words that were recognised. */
    private data class Columns(
        val time: Int? = null,
        val meters: Int? = null,
        val split: Int? = null,
        val rate: Int? = null,
    )

    private fun findColumns(header: List<OcrWord>): Columns {
        fun center(match: (String) -> Boolean) =
            header.firstOrNull { match(it.text.lowercase()) }?.let { (it.left + it.right) / 2 }
        return Columns(
            time = center { "time" in it },
            meters = center { "meter" in it },
            split = center { "500" in it || "50" in it && it.endsWith("m") && it != "m" },
            rate = center { it == "m" || "s/m" in it },
        )
    }

    /** Groups words whose vertical centres are within half a word height of each other. */
    private fun groupIntoLines(words: List<OcrWord>): List<List<OcrWord>> {
        if (words.isEmpty()) return emptyList()
        val tolerance = words.map { it.height }.sorted()[words.size / 2] * 0.5
        val lines = mutableListOf<MutableList<OcrWord>>()
        for (word in words.sortedBy { it.centerY }) {
            val line = lines.lastOrNull()
            if (line != null && abs(word.centerY - line.map { it.centerY }.average()) <= tolerance) {
                line += word
            } else {
                lines += mutableListOf(word)
            }
        }
        return lines.map { line -> line.sortedBy { it.left } }
    }

    /**
     * Fixes letters commonly read in place of digits, but only in tokens that are mostly digits,
     * so words like "time" and "meter" are left alone.
     */
    internal fun normalizeToken(token: String): String {
        val digits = token.count(Char::isDigit)
        val lookalikes = token.count { it in LOOKALIKES }
        if (digits == 0 || digits + lookalikes < token.length / 2.0) return token
        return token.map { LOOKALIKES[it] ?: it }.joinToString("")
    }

    private val LOOKALIKES = mapOf(
        'O' to '0', 'o' to '0', 'D' to '0', 'Q' to '0',
        'I' to '1', 'l' to '1', '|' to '1', 'i' to '1',
        'Z' to '2', 'z' to '2',
        'S' to '5', 's' to '5',
        'B' to '8', 'g' to '9',
        ';' to ':',
    )

    private val DISTANCE_INTERVALS = Regex("""(\d{1,2})\s*[x×X]\s*(\d{2,5})\s*m\W*(\d{1,2})[.:](\d{2})\s*r""")
    private val TIME_INTERVALS = Regex("""(\d{1,2})\s*[x×X]\s*(\d{1,2})[.:](\d{2})\W*(\d{1,2})[.:](\d{2})\s*r""")
    private val SINGLE_DISTANCE = Regex("""^\s*(\d{3,5})\s*m\s*$""")
    // A time on a line of its own, without tenths, unlike the times in the table.
    private val SINGLE_TIME = Regex("""^\s*(?:(\d{1,2}):)?(\d{1,2}):(\d{2})\s*$""")

    private fun parseWorkout(line: String): Pm5Workout? {
        DISTANCE_INTERVALS.find(line)?.destructured?.let { (count, meters, restMin, restSec) ->
            return Pm5Workout.DistanceIntervals(count.toInt(), meters.toInt(), restMin.toInt() * 60 + restSec.toInt())
        }
        TIME_INTERVALS.find(line)?.destructured?.let { (count, workMin, workSec, restMin, restSec) ->
            return Pm5Workout.TimeIntervals(
                count.toInt(),
                workMin.toInt() * 60 + workSec.toInt(),
                restMin.toInt() * 60 + restSec.toInt(),
            )
        }
        SINGLE_DISTANCE.find(line)?.destructured?.let { (meters) -> return Pm5Workout.SingleDistance(meters.toInt()) }
        SINGLE_TIME.find(line)?.destructured?.let { (hours, minutes, seconds) ->
            if (seconds.toInt() >= 60) return null
            return Pm5Workout.SingleTime(hours.ifEmpty { "0" }.toInt() * 3600 + minutes.toInt() * 60 + seconds.toInt())
        }
        return null
    }

    private val DATE = Regex("""(?i)\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s*(\d{1,2})\s*,?\s*(\d{4})""")

    private fun parseDate(line: String): LocalDate? {
        val (month, day, year) = DATE.find(line)?.destructured ?: return null
        val monthNumber = Month.entries.first { it.name.startsWith(month.uppercase()) }
        return runCatching { LocalDate.of(year.toInt(), monthNumber, day.toInt()) }.getOrNull()
    }

    // The point before the tenths is sometimes lost ("1:443"); with exactly one digit after the
    // seconds, it can only be the tenths.
    private val TIME_TOKEN = Regex("""^(?:(\d)[.:])?(\d{1,2})[.:](\d{2})[.,:]?(\d)$""")
    private val NUMBER_TOKEN = Regex("""^\d{1,5}$""")

    /** "1:44.5" or "1:02:03.4" in seconds; accepts ":" and "." mixed up, as recognisers often do. */
    internal fun parseTime(token: String): Double? {
        val (hours, minutes, seconds, tenths) = TIME_TOKEN.find(token)?.destructured ?: return null
        if (seconds.toInt() >= 60) return null
        return (hours.ifEmpty { "0" }.toInt() * 3600 + minutes.toInt() * 60 + seconds.toInt()) + tenths.toInt() / 10.0
    }

    /**
     * A table row: time, metres, split and stroke rate. Times and whole numbers are told apart by
     * their format, and each is placed in a column by its position under the header when the
     * header was read; otherwise by order, so a misread value doesn't shift the others.
     */
    private fun parseRow(line: List<OcrWord>, columns: Columns): Pm5Row? {
        val tokens = line.map { word -> normalizeToken(word.text) to (word.left + word.right) / 2 }
            .filter { (text, _) -> text.isNotBlank() }
        // Data rows are numbers only; this keeps out lines like "Sep 15 2026" and "Total Time: 22:35.4".
        if (tokens.any { (text, _) -> text.any(Char::isLetter) }) return null
        val times = tokens.mapNotNull { (text, x) -> parseTime(text)?.let { it to x } }
        val numbers = tokens.mapNotNull { (text, x) -> text.takeIf { NUMBER_TOKEN.matches(it) }?.toInt()?.let { it to x } }
        if (times.isEmpty() || times.size + numbers.size < 3) return null

        val (time, split) = when {
            times.size >= 2 -> times[0].first to times[1].first
            columns.time != null && columns.split != null -> {
                val (value, x) = times[0]
                if (abs(x - columns.time) < abs(x - columns.split)) value to null else null to value
            }
            // A lone time is most likely the split, which sits next to the stroke rate.
            else -> null to times[0].first
        }
        val (meters, rate) = when {
            numbers.size >= 2 -> numbers[0].first to numbers[1].first
            numbers.size == 1 -> {
                val (value, x) = numbers[0]
                val rightOfSplit = columns.split?.let { x > it } ?: (value <= MAX_STROKE_RATE)
                if (rightOfSplit) null to value else value to null
            }
            else -> null to null
        }
        return Pm5Row(timeSeconds = time, meters = meters, splitSeconds = split, strokeRate = rate)
    }

    /**
     * The PM5 lists the summary row first, but it may not have been recognised. It's told apart
     * from an ordinary row by covering the whole workout: the full distance, or the time of all
     * intervals together, rather than one interval's.
     */
    private fun separateTotal(rows: List<Pm5Row>, workout: Pm5Workout?): Pair<Pm5Row?, List<Pm5Row>> {
        if (rows.size < 2) return null to rows
        val first = rows.first()
        val rest = rows.drop(1)
        val isSummary = when (workout) {
            is Pm5Workout.DistanceIntervals -> first.meters?.let { it != workout.meters } ?: looksLonger(first, rest)
            is Pm5Workout.TimeIntervals ->
                first.timeSeconds?.let { abs(it - workout.workSeconds) > 1 } ?: looksLonger(first, rest)
            is Pm5Workout.SingleDistance -> first.meters?.let { it == workout.meters } ?: looksLonger(first, rest)
            is Pm5Workout.SingleTime ->
                first.timeSeconds?.let { abs(it - workout.seconds) <= 1 } ?: looksLonger(first, rest)
            null -> looksLonger(first, rest)
        }
        return if (isSummary) first to rest else null to rows
    }

    /** Whether [row] took much longer than the typical row in [others], as a whole workout does. */
    private fun looksLonger(row: Pm5Row, others: List<Pm5Row>): Boolean {
        val time = row.timeSeconds ?: return true // the screen's layout: summary first
        val typical = others.mapNotNull { it.timeSeconds }.sorted().let { it.getOrNull(it.size / 2) } ?: return true
        return time > typical * 1.5
    }

    /** A single piece shows running totals (500, 1000, 1500…); intervals show each one's own distance. */
    private fun toPerRowDistances(rows: List<Pm5Row>, total: Pm5Row?, workout: Pm5Workout?): List<Pm5Row> {
        // A timed piece shows each split's own distance, which may well be increasing.
        if (workout is Pm5Workout.SingleTime) return rows
        val meters = rows.map { it.meters }
        if (meters.any { it == null } || meters.size < 2) return rows
        val values = meters.filterNotNull()
        val increasing = values.zipWithNext().all { (a, b) -> b > a }
        val endsAtTotal = values.last() == (total?.meters ?: (workout as? Pm5Workout.SingleDistance)?.meters)
        if (!(increasing && (endsAtTotal || workout is Pm5Workout.SingleDistance))) return rows
        return rows.mapIndexed { index, row -> row.copy(meters = values[index] - (values.getOrNull(index - 1) ?: 0)) }
    }

    /** A timed piece shows running times (2:00, 4:00, 6:00…) instead; other workouts show each row's own. */
    private fun toPerRowTimes(rows: List<Pm5Row>, total: Pm5Row?, workout: Pm5Workout?): List<Pm5Row> {
        if (workout !is Pm5Workout.SingleTime) return rows
        val times = rows.map { it.timeSeconds }
        if (times.any { it == null } || times.size < 2) return rows
        val values = times.filterNotNull()
        val increasing = values.zipWithNext().all { (a, b) -> b > a }
        val endsAtTotal = abs(values.last() - (total?.timeSeconds ?: workout.seconds.toDouble())) <= 0.1
        if (!(increasing && endsAtTotal)) return rows
        return rows.mapIndexed { index, row -> row.copy(timeSeconds = values[index] - (values.getOrNull(index - 1) ?: 0.0)) }
    }

    private fun checkRow(row: Pm5Row): Pm5Row {
        val problem = when {
            row.splitSeconds == null -> "Couldn't read the split"
            row.meters == null -> "Couldn't read the distance"
            row.strokeRate == null -> "Couldn't read the stroke rate"
            row.strokeRate !in MIN_STROKE_RATE..MAX_STROKE_RATE -> "Stroke rate ${row.strokeRate} looks wrong"
            row.timeSeconds != null && !timeMatchesSplit(row.timeSeconds, row.meters, row.splitSeconds) ->
                "Time and split don't match"
            else -> null
        }
        return row.copy(problem = problem)
    }

    /** Time ≈ split × distance / 500, allowing for the split being rounded to tenths. */
    private fun timeMatchesSplit(time: Double, meters: Int, split: Double): Boolean {
        val expected = split * meters / 500
        return abs(time - expected) <= 0.05 * meters / 500 + 0.15
    }

    private fun overallProblems(workout: Pm5Workout?, total: Pm5Row?, rows: List<Pm5Row>): List<String> = buildList {
        if (workout == null) add("Couldn't read the workout type at the top of the screen")
        if (rows.isEmpty()) {
            add("Couldn't find the results table")
            return@buildList
        }
        val expectedCount = when (workout) {
            is Pm5Workout.DistanceIntervals -> workout.count
            is Pm5Workout.TimeIntervals -> workout.count
            else -> null
        }
        if (expectedCount != null && rows.size != expectedCount) add("Found ${rows.size} of $expectedCount intervals")
        val totalTime = total?.timeSeconds
        if (totalTime != null && rows.all { it.timeSeconds != null }) {
            val sum = rows.sumOf { it.timeSeconds!! }
            if (abs(sum - totalTime) > 0.1 * rows.size + 0.1) {
                add("The rows add up to ${formatTime(sum)}, but the total says ${formatTime(totalTime)}")
            }
        }
        if (workout is Pm5Workout.SingleDistance && rows.all { it.meters != null }) {
            val sum = rows.sumOf { it.meters!! }
            if (sum != workout.meters) add("The splits add up to $sum m, not ${workout.meters} m")
        }
        val totalMeters = total?.meters
        if (workout is Pm5Workout.SingleTime && totalMeters != null && rows.all { it.meters != null }) {
            val sum = rows.sumOf { it.meters!! }
            if (sum != totalMeters) add("The splits add up to $sum m, but the total says $totalMeters m")
        }
    }

    private fun formatTime(seconds: Double): String {
        val tenths = (seconds * 10).roundToInt()
        return "%d:%02d.%d".format(tenths / 600, tenths % 600 / 10, tenths % 10)
    }

    private const val MIN_STROKE_RATE = 10
    private const val MAX_STROKE_RATE = 60
}

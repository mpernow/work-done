package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class Pm5ParserTest {
    /** Words exactly as ML Kit recognised them in a real photo, one per line: text, left, top, right, bottom. */
    private fun fixture(name: String): List<OcrWord> =
        javaClass.getResource("/pm5/$name")!!.readText().lines().filter { it.isNotBlank() }.map { line ->
            val parts = line.split('\t')
            OcrWord(parts[0], parts[1].toInt(), parts[2].toInt(), parts[3].toInt(), parts[4].toInt())
        }

    /** A row of words laid out like the PM5 table, for hand-made screens. */
    private fun row(y: Int, vararg cells: String): List<OcrWord> =
        cells.mapIndexed { i, text -> OcrWord(text, 700 + i * 400, y, 1000 + i * 400, y + 90) }

    private val header = row(1700, "time", "meter", "/500m", "s/m")

    @Test
    fun readsRealPhotoOfDistanceIntervals() {
        // Recognised with "1:443" (no point), "1;44.9", commas for points and "B000" for 3000.
        val result = Pm5Parser.parse(fixture("intervals_distance.tsv"))

        assertEquals(Pm5Workout.DistanceIntervals(count = 6, meters = 500, restSeconds = 120), result.workout)
        assertEquals(LocalDate.of(2026, 9, 15), result.date)
        assertEquals(635.4, result.total!!.timeSeconds!!, 1e-9)
        assertEquals(listOf(104.5, 104.3, 104.9, 105.6, 107.3, 108.8), result.rows.map { it.splitSeconds })
        assertEquals(listOf(104.5, 104.3, 104.9, 105.6, 107.3, 108.8), result.rows.map { it.timeSeconds })
        assertEquals(List(6) { 500 }, result.rows.map { it.meters })
        assertEquals(listOf(28, 28, 29, 28, 27, 28), result.rows.map { it.strokeRate })
        assertTrue(result.rows.all { it.problem == null })
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun readsRealPhotoOfASingleDistanceWithGlare() {
        val result = Pm5Parser.parse(fixture("single_distance.tsv"))

        assertEquals(Pm5Workout.SingleDistance(2000), result.workout)
        assertEquals(LocalDate.of(2026, 9, 8), result.date)
        // The screen shows running totals (500, 1000, 1500, 2000); each split is 500 m.
        assertEquals(List(4) { 500 }, result.rows.map { it.meters })
        assertEquals(listOf(109.4, 110.2, 154.9, 143.2), result.rows.map { it.splitSeconds })
        assertEquals(listOf(28, 28, 19, 21), result.rows.map { it.strokeRate })
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun readsTimeIntervals() {
        val words = row(1400, "8x2:00/1:00r") + header +
            row(1850, "16:00.0", "4120", "1:56.5", "26") +
            (0 until 8).flatMap { i -> row(2000 + i * 130, "2:00.0", "515", "1:56.5", "26") }

        val result = Pm5Parser.parse(words)

        assertEquals(Pm5Workout.TimeIntervals(count = 8, workSeconds = 120, restSeconds = 60), result.workout)
        assertEquals(8, result.rows.size)
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun flagsARowWhoseTimeDoesNotMatchItsSplit() {
        val words = row(1400, "2x500m/2:00r") + header +
            row(1850, "3:30.0", "1000", "1:45.0", "28") +
            row(2000, "1:45.0", "500", "1:45.0", "28") +
            row(2130, "1:45.0", "500", "1:48.0", "28") // split misread: 1:48.0 for 1:45.0

        val result = Pm5Parser.parse(words)

        assertNull(result.rows[0].problem)
        assertEquals("Time and split don't match", result.rows[1].problem)
    }

    @Test
    fun reportsMissingIntervalsAndTotalsThatDoNotAddUp() {
        val words = row(1400, "3x500m/1:00r") + header +
            row(1850, "5:15.0", "1500", "1:45.0", "28") +
            row(2000, "1:45.0", "500", "1:45.0", "28") +
            row(2130, "1:45.0", "500", "1:45.0", "28")

        val result = Pm5Parser.parse(words)

        assertEquals(
            listOf("Found 2 of 3 intervals", "The rows add up to 3:30.0, but the total says 5:15.0"),
            result.problems,
        )
    }

    @Test
    fun keepsAllRowsWhenTheSummaryRowWasNotRecognised() {
        val words = row(1400, "2x500m/1:00r") + header +
            row(2000, "1:45.0", "500", "1:45.0", "28") +
            row(2130, "1:46.0", "500", "1:46.0", "27")

        val result = Pm5Parser.parse(words)

        assertNull(result.total)
        assertEquals(2, result.rows.size)
    }

    @Test
    fun fixesLettersReadAsDigitsOnlyInNumbers() {
        assertEquals("1:08.0", Pm5Parser.normalizeToken("1:O8.O"))
        assertEquals("8000", Pm5Parser.normalizeToken("B000"))
        assertEquals("meter", Pm5Parser.normalizeToken("meter"))
    }

    @Test
    fun timesAcceptCommonMisreadings() {
        assertEquals(104.5, Pm5Parser.parseTime("1:44.5")!!, 1e-9)
        assertEquals(104.5, Pm5Parser.parseTime("1.44.5")!!, 1e-9)
        assertEquals(104.5, Pm5Parser.parseTime("1:44,5")!!, 1e-9)
        assertEquals(104.3, Pm5Parser.parseTime("1:443")!!, 1e-9)
        assertEquals(3723.4, Pm5Parser.parseTime("1:02:03.4")!!, 1e-9)
        assertNull(Pm5Parser.parseTime("1:44"))
        assertNull(Pm5Parser.parseTime("1:64.5"))
    }
}

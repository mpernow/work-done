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
    fun joinsRealPhotosOfAScrolledScreenDespiteAMisreadRate() {
        // Rows 1–8, then 3–9 and the rest distance ("r188"); the tenth wasn't in view. The first
        // photo has "B0" for the fourth row's rate of 30, and "i:30r" in the workout.
        val result = Pm5Parser.parsePhotos(listOf(fixture("intervals_time_part1.tsv"), fixture("intervals_time_part2.tsv")))

        assertEquals(Pm5Workout.TimeIntervals(count = 10, workSeconds = 60, restSeconds = 90), result.workout)
        assertEquals(listOf(299, 298, 296, 292, 292, 295, 292, 294, 289), result.rows.map { it.meters })
        assertEquals(listOf(31, 31, 31, 30, 30, 31, 30, 31, 30), result.rows.map { it.strokeRate })
        assertTrue(result.rows.all { it.problem == null })
        assertEquals(
            listOf("Found 9 of 10 intervals", "The rows add up to 9:00.0, but the total says 10:00.0"),
            result.problems,
        )
    }

    @Test
    fun readsRealPhotoOfASingleTime() {
        val result = Pm5Parser.parse(fixture("single_time.tsv"))

        assertEquals(Pm5Workout.SingleTime(600), result.workout)
        assertEquals(listOf(465, 470, 474, 478, 484), result.rows.map { it.meters })
        assertEquals(List(5) { 120.0 }, result.rows.map { it.timeSeconds })
        assertEquals(listOf(20, 22, 21, 22, 23), result.rows.map { it.strokeRate })
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun readsASingleTime() {
        // The screen from a 10:00 piece: running times, but each split's own distance.
        val words = row(1300, "10:00") + row(1450, "Oct", "16", "2026") + header +
            row(1850, "10:00.0", "2371", "2:06.5", "21") +
            row(2000, "2:00.0", "465", "2:09.0", "20") +
            row(2130, "4:00.0", "470", "2:07.6", "22") +
            row(2260, "6:00.0", "474", "2:06.5", "21") +
            row(2390, "8:00.0", "478", "2:05.5", "22") +
            row(2520, "10:00.0", "484", "2:03.9", "23")

        val result = Pm5Parser.parse(words)

        assertEquals(Pm5Workout.SingleTime(600), result.workout)
        assertEquals(LocalDate.of(2026, 10, 16), result.date)
        assertEquals(2371, result.total!!.meters)
        assertEquals(List(5) { 120.0 }, result.rows.map { it.timeSeconds })
        assertEquals(listOf(465, 470, 474, 478, 484), result.rows.map { it.meters })
        assertEquals(listOf(129.0, 127.6, 126.5, 125.5, 123.9), result.rows.map { it.splitSeconds })
        assertEquals(listOf(20, 22, 21, 22, 23), result.rows.map { it.strokeRate })
        assertTrue(result.rows.all { it.problem == null })
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun flagsATimedPieceWhoseSplitsDoNotAddUpToTheTotalDistance() {
        val words = row(1300, "4:00") + header +
            row(1850, "4:00.0", "950", "2:06.3", "21") +
            row(2000, "2:00.0", "465", "2:09.0", "20") +
            row(2130, "4:00.0", "407", "2:07.6", "22") // 470 misread as 407

        val result = Pm5Parser.parse(words)

        assertEquals(listOf("The splits add up to 872 m, but the total says 950 m"), result.problems)
    }

    /** A photo of a "10x1:00/1:30r" screen showing the given intervals' metres, splits and rates. */
    private fun tenMinuteIntervals(vararg rows: Triple<String, String, String>): List<OcrWord> =
        row(1300, "10x1:00/1:30r") + row(1450, "Oct", "16", "2026") + header +
            row(1850, "10:00.0", "2941", "1:42.0", "30") +
            rows.flatMapIndexed { i, (meters, split, rate) -> row(2000 + i * 130, "1:00.0", meters, split, rate) }

    private val tenIntervals = listOf(
        Triple("299", "1:40.3", "31"), Triple("298", "1:40.6", "31"), Triple("296", "1:41.3", "31"),
        Triple("292", "1:42.7", "30"), Triple("292", "1:42.7", "30"), Triple("295", "1:41.6", "31"),
        Triple("292", "1:42.7", "30"), Triple("294", "1:42.0", "31"), Triple("289", "1:43.8", "30"),
        Triple("294", "1:42.0", "31"),
    )

    @Test
    fun joinsPhotosOfAScrolledScreenWhereTheirRowsOverlap() {
        val first = tenMinuteIntervals(*tenIntervals.subList(0, 8).toTypedArray())
        val second = tenMinuteIntervals(*tenIntervals.subList(3, 10).toTypedArray())

        val result = Pm5Parser.parsePhotos(listOf(first, second))

        assertEquals(tenIntervals.map { it.first.toInt() }, result.rows.map { it.meters })
        assertEquals(List(10) { 60.0 }, result.rows.map { it.timeSeconds })
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun saysWhenThePhotosStillMissTheLastIntervals() {
        // Two real photos: rows 1–8, then 3–9 and the rest distance ("r188"); the tenth wasn't in view.
        val first = tenMinuteIntervals(*tenIntervals.subList(0, 8).toTypedArray())
        val second = tenMinuteIntervals(*tenIntervals.subList(2, 9).toTypedArray()) + row(2910, "r188")

        val result = Pm5Parser.parsePhotos(listOf(first, second))

        assertEquals(tenIntervals.take(9).map { it.first.toInt() }, result.rows.map { it.meters })
        assertTrue("Found 9 of 10 intervals" in result.problems)
    }

    @Test
    fun takesValuesMisreadInOnePhotoFromTheOther() {
        val first = tenMinuteIntervals(*tenIntervals.subList(0, 7).toTypedArray(), Triple("294", "1:4?", "31"))
        val second = tenMinuteIntervals(*tenIntervals.subList(7, 10).toTypedArray())

        val result = Pm5Parser.parsePhotos(listOf(first, second))

        assertEquals(10, result.rows.size)
        assertEquals(102.0, result.rows[7].splitSeconds!!, 1e-9)
        assertTrue(result.rows.all { it.problem == null })
    }

    @Test
    fun joinsRunningTotalsOfASingleDistanceBeforeTakingTheirDifferences() {
        fun photo(range: IntRange) = row(1300, "5000m") + header +
            row(1850, "17:30.0", "5000", "1:45.0", "28") +
            range.mapIndexed { i, split -> row(2000 + i * 130, "1:45.0", "${split * 500}", "1:45.0", "28") }.flatten()

        val result = Pm5Parser.parsePhotos(listOf(photo(1..8), photo(3..10)))

        assertEquals(List(10) { 500 }, result.rows.map { it.meters })
        assertEquals(emptyList<String>(), result.problems)
    }

    @Test
    fun saysWhenPhotosHaveNoRowsInCommon() {
        fun photo(vararg splits: String) = row(1300, "4x500m/2:00r") + header +
            row(1850, "7:00.0", "2000", "1:45.0", "28") +
            splits.mapIndexed { i, split -> row(2000 + i * 130, split, "500", split, "28") }.flatten()

        val result = Pm5Parser.parsePhotos(listOf(photo("1:44.0", "1:45.0", "1:46.0"), photo("1:47.0", "1:48.0", "1:49.0")))

        assertEquals(6, result.rows.size)
        assertTrue(result.problems.any { it.startsWith("Couldn't tell where photo 2 carries on from photo 1") })
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

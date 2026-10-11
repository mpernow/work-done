package com.mpernow.workdone.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetInputTest {
    @Test
    fun parsesWeightAndRepsInKilograms() {
        val values = SetInput.parse(SetDraft(weight = "82.5", reps = "8"), TrackingType.WEIGHT_REPS, WeightUnit.KG)
        assertEquals(SetValues(reps = 8, weightKg = 82.5), values)
    }

    @Test
    fun acceptsCommaAsDecimalSeparator() {
        val values = SetInput.parse(SetDraft(weight = "82,5", reps = "8"), TrackingType.WEIGHT_REPS, WeightUnit.KG)
        assertEquals(82.5, values?.weightKg)
    }

    @Test
    fun poundsAreStoredAsKilograms() {
        val values = SetInput.parse(SetDraft(weight = "225", reps = "5"), TrackingType.WEIGHT_REPS, WeightUnit.LB)
        assertEquals(102.058, values!!.weightKg!!, 0.001)
    }

    @Test
    fun poundsSurviveARoundTrip() {
        val values = SetInput.parse(SetDraft(weight = "225", reps = "5"), TrackingType.WEIGHT_REPS, WeightUnit.LB)!!
        assertEquals("225", SetInput.draftFrom(values, WeightUnit.LB).weight)
    }

    @Test
    fun zeroWeightIsAllowedButZeroRepsIsNot() {
        assertEquals(0.0, SetInput.parse(SetDraft(weight = "0", reps = "10"), TrackingType.WEIGHT_REPS, WeightUnit.KG)?.weightKg)
        assertNull(SetInput.parse(SetDraft(weight = "50", reps = "0"), TrackingType.WEIGHT_REPS, WeightUnit.KG))
    }

    @Test
    fun missingRequiredFieldGivesNull() {
        assertNull(SetInput.parse(SetDraft(reps = "8"), TrackingType.WEIGHT_REPS, WeightUnit.KG))
        assertNull(SetInput.parse(SetDraft(), TrackingType.REPS, WeightUnit.KG))
        assertNull(SetInput.parse(SetDraft(), TrackingType.DURATION, WeightUnit.KG))
    }

    @Test
    fun durationCombinesMinutesAndSeconds() {
        assertEquals(90, SetInput.parse(SetDraft(minutes = "1", seconds = "30"), TrackingType.DURATION, WeightUnit.KG)?.durationSeconds)
        assertEquals(45, SetInput.parse(SetDraft(seconds = "45"), TrackingType.DURATION, WeightUnit.KG)?.durationSeconds)
        assertEquals(95 * 60, SetInput.parse(SetDraft(minutes = "95"), TrackingType.DURATION, WeightUnit.KG)?.durationSeconds)
    }

    @Test
    fun secondsMustBeUnderSixty() {
        assertNull(SetInput.parse(SetDraft(minutes = "1", seconds = "60"), TrackingType.DURATION, WeightUnit.KG))
    }

    @Test
    fun distanceIsStoredInMetres() {
        val values = SetInput.parse(
            SetDraft(distanceKm = "5,2", minutes = "26"),
            TrackingType.DISTANCE_TIME,
            WeightUnit.KG,
        )
        assertEquals(SetValues(durationSeconds = 26 * 60, distanceMeters = 5200.0), values)
    }

    @Test
    fun describesEachTrackingType() {
        assertEquals("80 kg × 8", SetInput.describe(SetValues(reps = 8, weightKg = 80.0), TrackingType.WEIGHT_REPS, WeightUnit.KG))
        assertEquals("12 reps", SetInput.describe(SetValues(reps = 12), TrackingType.REPS, WeightUnit.KG))
        assertEquals("1:05", SetInput.describe(SetValues(durationSeconds = 65), TrackingType.DURATION, WeightUnit.KG))
        assertEquals(
            "10.5 km in 1:02:03",
            SetInput.describe(SetValues(durationSeconds = 3723, distanceMeters = 10_500.0), TrackingType.DISTANCE_TIME, WeightUnit.KG),
        )
    }

    @Test
    fun formatDecimalDropsTrailingZeros() {
        assertEquals("80", SetInput.formatDecimal(80.0))
        assertEquals("82.5", SetInput.formatDecimal(82.50))
        assertEquals("0", SetInput.formatDecimal(0.0))
        assertEquals("1.23", SetInput.formatDecimal(1.234))
    }

    @Test
    fun timeIntervalsNeedTimeAndSplit() {
        val draft = SetDraft(workTime = "200", split = "1524", rest = "100", resistance = "6")
        assertEquals(
            SetValues(durationSeconds = 120, splitSeconds = 112.4, restSeconds = 60, resistance = 6),
            SetInput.parse(draft, TrackingType.INTERVALS_TIME, WeightUnit.KG),
        )
        assertNull(SetInput.parse(draft.copy(split = ""), TrackingType.INTERVALS_TIME, WeightUnit.KG))
        assertNull(SetInput.parse(draft.copy(workTime = ""), TrackingType.INTERVALS_TIME, WeightUnit.KG))
    }

    @Test
    fun distanceIntervalsNeedMetresAndSplitButRestAndResistanceAreOptional() {
        val values = SetInput.parse(SetDraft(distanceMeters = "500", split = "1550"), TrackingType.INTERVALS_DISTANCE, WeightUnit.KG)
        assertEquals(SetValues(distanceMeters = 500.0, splitSeconds = 115.0), values)
    }

    @Test
    fun invalidOptionalIntervalFieldsBlockTheSet() {
        val draft = SetDraft(distanceMeters = "500", split = "1550", rest = "190")
        assertNull(SetInput.parse(draft, TrackingType.INTERVALS_DISTANCE, WeightUnit.KG))
    }

    @Test
    fun intervalsPrefillAndDescribe() {
        val values = SetValues(distanceMeters = 500.0, splitSeconds = 112.4, restSeconds = 90, resistance = 6)
        val draft = SetInput.draftFrom(values, WeightUnit.KG)
        assertEquals(SetDraft(distanceKm = "0.5", distanceMeters = "500", split = "1524", rest = "130", resistance = "6"), draft)
        assertEquals(values, SetInput.parse(draft, TrackingType.INTERVALS_DISTANCE, WeightUnit.KG))
        assertEquals(
            "500 m · 1:52.4 /500m · rest 1:30 · resistance 6",
            SetInput.describe(values, TrackingType.INTERVALS_DISTANCE, WeightUnit.KG),
        )
        assertEquals(
            "2:00 · 1:52.4 /500m",
            SetInput.describe(SetValues(durationSeconds = 120, splitSeconds = 112.4), TrackingType.INTERVALS_TIME, WeightUnit.KG),
        )
    }
}

package com.mpernow.workdone.domain

enum class ExerciseCategory(val label: String) {
    CHEST("Chest"),
    BACK("Back"),
    SHOULDERS("Shoulders"),
    ARMS("Arms"),
    LEGS("Legs"),
    CORE("Core"),
    CARDIO("Cardio"),
    OTHER("Other"),
}

/** Decides which set fields are filled in for an exercise. */
enum class TrackingType(val label: String) {
    WEIGHT_REPS("Weight × reps"),
    REPS("Reps only"),
    DURATION("Duration"),
    DISTANCE_TIME("Distance + time"),
}

enum class WeightUnit(val label: String) { KG("kg"), LB("lb") }

/** The measured values of one set, in storage units (kilograms, seconds, metres). */
data class SetValues(
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
)

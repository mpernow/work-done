package com.mpernow.workdone.domain

/** Pure statistics helpers, kept free of Android types so they can be unit tested on the JVM. */
object Stats {
    /** Estimated one-rep max using the Epley formula: weight × (1 + reps / 30). */
    fun epleyOneRepMax(weightKg: Double, reps: Int): Double = when {
        reps <= 0 -> 0.0
        reps == 1 -> weightKg
        else -> weightKg * (1 + reps / 30.0)
    }

    /** Volume of a single set: reps × weight. */
    fun volume(weightKg: Double, reps: Int): Double = weightKg * reps
}

package com.mpernow.workdone.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mpernow.workdone.domain.SetValues
import com.mpernow.workdone.domain.TrackingType
import java.time.Instant

@Entity(tableName = "exercises", indices = [Index(value = ["name"], unique = true)])
data class ExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val trackingType: TrackingType,
)

@Entity(tableName = "workouts", indices = [Index("startedAt")])
data class WorkoutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    /** Null while the workout is in progress. */
    val finishedAt: Instant? = null,
    val notes: String = "",
)

/**
 * One set within a workout. Weight is always stored in kilograms and distance in metres;
 * the unit setting only affects display and input.
 */
@Entity(
    tableName = "set_entries",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ExerciseEntity::class,
            parentColumns = ["id"],
            childColumns = ["exerciseId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("workoutId"), Index("exerciseId")],
)
data class SetEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workoutId: Long,
    val exerciseId: Long,
    /** Order of the set within its workout. */
    val position: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSeconds: Int? = null,
    val distanceMeters: Double? = null,
    val restSeconds: Int? = null,
    /** Seconds per 500 m. */
    val splitSeconds: Double? = null,
    val resistance: Int? = null,
)

fun SetEntryEntity.toValues() = SetValues(
    reps = reps,
    weightKg = weightKg,
    durationSeconds = durationSeconds,
    distanceMeters = distanceMeters,
    restSeconds = restSeconds,
    splitSeconds = splitSeconds,
    resistance = resistance,
)

package com.mpernow.workdone.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.mpernow.workdone.domain.SetValues
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

    /**
     * Most used first, counted as the number of workouts an exercise appears in; exercises
     * used equally often, or never, are alphabetical.
     */
    @Query(
        """
        SELECT e.* FROM exercises e
        LEFT JOIN (
            SELECT exerciseId, COUNT(DISTINCT workoutId) AS workouts FROM set_entries GROUP BY exerciseId
        ) usage ON usage.exerciseId = e.id
        ORDER BY COALESCE(usage.workouts, 0) DESC, e.name COLLATE NOCASE
        """
    )
    fun observeByUsage(): Flow<List<ExerciseEntity>>

    @Query("SELECT * FROM exercises WHERE id = :id")
    fun observe(id: Long): Flow<ExerciseEntity?>

    @Query("SELECT * FROM exercises WHERE id = :id")
    suspend fun get(id: Long): ExerciseEntity?

    @Query("SELECT * FROM exercises WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): ExerciseEntity?

    @Insert
    suspend fun insert(exercise: ExerciseEntity): Long

    @Update
    suspend fun update(exercise: ExerciseEntity)

    @Delete
    suspend fun delete(exercise: ExerciseEntity)
}

@Dao
interface WorkoutDao {
    @Query("SELECT * FROM workouts ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<WorkoutEntity>>

    @Query("SELECT * FROM workouts WHERE id = :id")
    fun observe(id: Long): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE id = :id")
    suspend fun get(id: Long): WorkoutEntity?

    @Query("SELECT * FROM workouts WHERE finishedAt IS NOT NULL ORDER BY startedAt DESC")
    fun observeFinished(): Flow<List<WorkoutEntity>>

    @Query("SELECT COUNT(*) FROM workouts")
    suspend fun count(): Int

    @Query("SELECT MAX(startedAt) FROM workouts")
    suspend fun latestStart(): Instant?

    /** The workout in progress, if any. */
    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeActive(): Flow<WorkoutEntity?>

    @Query("SELECT * FROM workouts WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun getActive(): WorkoutEntity?

    @Insert
    suspend fun insert(workout: WorkoutEntity): Long

    @Update
    suspend fun update(workout: WorkoutEntity)

    @Delete
    suspend fun delete(workout: WorkoutEntity)
}

@Dao
interface SetEntryDao {
    @Query("SELECT * FROM set_entries WHERE workoutId = :workoutId ORDER BY position")
    fun observeForWorkout(workoutId: Long): Flow<List<SetEntryEntity>>

    @Query(
        """
        SELECT s.* FROM set_entries s
        JOIN workouts w ON w.id = s.workoutId
        WHERE s.exerciseId = :exerciseId
        ORDER BY w.startedAt, s.position
        """
    )
    fun observeForExercise(exerciseId: Long): Flow<List<SetEntryEntity>>

    /** Every set of an exercise in finished workouts, oldest first, for its progress charts. */
    @Query(
        """
        SELECT s.*, w.startedAt AS startedAt FROM set_entries s
        JOIN workouts w ON w.id = s.workoutId
        WHERE s.exerciseId = :exerciseId AND w.finishedAt IS NOT NULL
        ORDER BY w.startedAt, s.position
        """
    )
    fun observeFinishedWithDates(exerciseId: Long): Flow<List<SetWithDate>>

    /** Sets and volume of each finished workout, for the statistics screen. */
    @Query(
        """
        SELECT w.id AS workoutId, w.startedAt AS startedAt,
               COUNT(*) AS setCount, COALESCE(SUM(s.weightKg * s.reps), 0) AS volumeKg
        FROM set_entries s
        JOIN workouts w ON w.id = s.workoutId
        WHERE w.finishedAt IS NOT NULL
        GROUP BY w.id
        """
    )
    fun observeWorkoutTotals(): Flow<List<WorkoutTotalsRow>>

    /** The most recent set of an exercise, used to pre-fill the logging form. */
    @Query(
        """
        SELECT s.* FROM set_entries s
        JOIN workouts w ON w.id = s.workoutId
        WHERE s.exerciseId = :exerciseId
        ORDER BY w.startedAt DESC, s.position DESC
        LIMIT 1
        """
    )
    suspend fun lastForExercise(exerciseId: Long): SetEntryEntity?

    /** One row per exercise entry in each workout, for the history list. */
    @Query(
        """
        SELECT s.workoutId AS workoutId, e.name AS exerciseName,
               COUNT(*) AS setCount, MIN(s.position) AS firstPosition
        FROM set_entries s
        JOIN exercises e ON e.id = s.exerciseId
        GROUP BY s.workoutId, s.exerciseId, s.entry
        """
    )
    fun observeExerciseSummaries(): Flow<List<WorkoutExerciseSummary>>

    @Query("SELECT COUNT(*) FROM set_entries WHERE exerciseId = :exerciseId")
    suspend fun countForExercise(exerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM set_entries WHERE workoutId = :workoutId")
    suspend fun countForWorkout(workoutId: Long): Int

    /** Appends a set to the end of a workout. The position is computed in the same statement, so it can't race. */
    @Query(
        """
        INSERT INTO set_entries (
            workoutId, exerciseId, entry, position, reps, weightKg, durationSeconds, distanceMeters,
            restSeconds, splitSeconds, resistance, strokeRate
        )
        VALUES (
            :workoutId, :exerciseId, :entry,
            (SELECT COALESCE(MAX(position), -1) + 1 FROM set_entries WHERE workoutId = :workoutId),
            :reps, :weightKg, :durationSeconds, :distanceMeters, :restSeconds, :splitSeconds, :resistance, :strokeRate
        )
        """
    )
    suspend fun insertAtEnd(
        workoutId: Long,
        exerciseId: Long,
        reps: Int?,
        weightKg: Double?,
        durationSeconds: Int?,
        distanceMeters: Double?,
        restSeconds: Int? = null,
        splitSeconds: Double? = null,
        resistance: Int? = null,
        strokeRate: Int? = null,
        entry: Int = 0,
    ): Long

    /** The entry number for the exercise's next separate entry in the workout. */
    @Query("SELECT COALESCE(MAX(entry), -1) + 1 FROM set_entries WHERE workoutId = :workoutId AND exerciseId = :exerciseId")
    suspend fun nextEntry(workoutId: Long, exerciseId: Long): Int

    /** Adds [sets] to the end of the workout as a new entry of the exercise. */
    @Transaction
    suspend fun insertEntry(workoutId: Long, exerciseId: Long, sets: List<SetValues>) {
        val entry = nextEntry(workoutId, exerciseId)
        sets.forEach { values ->
            insertAtEnd(
                workoutId = workoutId,
                exerciseId = exerciseId,
                reps = values.reps,
                weightKg = values.weightKg,
                durationSeconds = values.durationSeconds,
                distanceMeters = values.distanceMeters,
                restSeconds = values.restSeconds,
                splitSeconds = values.splitSeconds,
                resistance = values.resistance,
                strokeRate = values.strokeRate,
                entry = entry,
            )
        }
    }

    @Insert
    suspend fun insert(set: SetEntryEntity): Long

    @Update
    suspend fun update(set: SetEntryEntity)

    @Delete
    suspend fun delete(set: SetEntryEntity)
}

data class WorkoutExerciseSummary(
    val workoutId: Long,
    val exerciseName: String,
    val setCount: Int,
    /** Position of the exercise's first set, to list exercises in the order they were done. */
    val firstPosition: Int,
)

data class SetWithDate(
    @Embedded val set: SetEntryEntity,
    val startedAt: Instant,
)

data class WorkoutTotalsRow(
    val workoutId: Long,
    val startedAt: Instant,
    val setCount: Int,
    val volumeKg: Double,
)

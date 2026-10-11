package com.mpernow.workdone.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<ExerciseEntity>>

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

    @Query("SELECT COUNT(*) FROM set_entries WHERE exerciseId = :exerciseId")
    suspend fun countForExercise(exerciseId: Long): Int

    @Query("SELECT COUNT(*) FROM set_entries WHERE workoutId = :workoutId")
    suspend fun countForWorkout(workoutId: Long): Int

    /** Appends a set to the end of a workout. The position is computed in the same statement, so it can't race. */
    @Query(
        """
        INSERT INTO set_entries (workoutId, exerciseId, position, reps, weightKg, durationSeconds, distanceMeters)
        VALUES (
            :workoutId, :exerciseId,
            (SELECT COALESCE(MAX(position), -1) + 1 FROM set_entries WHERE workoutId = :workoutId),
            :reps, :weightKg, :durationSeconds, :distanceMeters
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
    ): Long

    @Insert
    suspend fun insert(set: SetEntryEntity): Long

    @Update
    suspend fun update(set: SetEntryEntity)

    @Delete
    suspend fun delete(set: SetEntryEntity)
}

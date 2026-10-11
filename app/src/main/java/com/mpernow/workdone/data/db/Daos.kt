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

    @Insert
    suspend fun insert(set: SetEntryEntity): Long

    @Update
    suspend fun update(set: SetEntryEntity)

    @Delete
    suspend fun delete(set: SetEntryEntity)
}

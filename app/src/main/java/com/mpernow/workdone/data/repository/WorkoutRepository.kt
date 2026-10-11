package com.mpernow.workdone.data.repository

import com.mpernow.workdone.data.db.SetEntryDao
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.WorkoutDao
import com.mpernow.workdone.data.db.WorkoutEntity
import com.mpernow.workdone.domain.SetValues
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val setEntryDao: SetEntryDao,
) {
    fun observeWorkouts(): Flow<List<WorkoutEntity>> = workoutDao.observeAll()

    fun observeWorkout(id: Long): Flow<WorkoutEntity?> = workoutDao.observe(id)

    fun observeActiveWorkout(): Flow<WorkoutEntity?> = workoutDao.observeActive()

    fun observeSets(workoutId: Long): Flow<List<SetEntryEntity>> = setEntryDao.observeForWorkout(workoutId)

    fun observeSetsForExercise(exerciseId: Long): Flow<List<SetEntryEntity>> =
        setEntryDao.observeForExercise(exerciseId)

    suspend fun lastSetFor(exerciseId: Long): SetEntryEntity? = setEntryDao.lastForExercise(exerciseId)

    /** Starts a workout, or returns the one already in progress. */
    suspend fun startWorkout(now: Instant): Long =
        workoutDao.getActive()?.id ?: workoutDao.insert(WorkoutEntity(startedAt = now))

    /** Marks the workout finished. A workout without any sets is deleted instead. */
    suspend fun finishWorkout(workoutId: Long, notes: String, now: Instant) {
        val workout = workoutDao.get(workoutId) ?: return
        if (setEntryDao.countForWorkout(workoutId) == 0) {
            workoutDao.delete(workout)
        } else {
            workoutDao.update(workout.copy(finishedAt = now, notes = notes.trim()))
        }
    }

    suspend fun saveWorkout(workout: WorkoutEntity): Long =
        if (workout.id == 0L) workoutDao.insert(workout) else workout.id.also { workoutDao.update(workout) }

    /** Deleting a workout also deletes its sets (foreign key cascade). */
    suspend fun deleteWorkout(workout: WorkoutEntity) = workoutDao.delete(workout)

    suspend fun addSet(workoutId: Long, exerciseId: Long, values: SetValues): Long =
        setEntryDao.insertAtEnd(
            workoutId = workoutId,
            exerciseId = exerciseId,
            reps = values.reps,
            weightKg = values.weightKg,
            durationSeconds = values.durationSeconds,
            distanceMeters = values.distanceMeters,
        )

    suspend fun saveSet(set: SetEntryEntity): Long =
        if (set.id == 0L) setEntryDao.insert(set) else set.id.also { setEntryDao.update(set) }

    suspend fun deleteSet(set: SetEntryEntity) = setEntryDao.delete(set)
}

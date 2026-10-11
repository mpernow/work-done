package com.mpernow.workdone.data.repository

import com.mpernow.workdone.data.db.SetEntryDao
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.WorkoutDao
import com.mpernow.workdone.data.db.WorkoutEntity
import kotlinx.coroutines.flow.Flow

class WorkoutRepository(
    private val workoutDao: WorkoutDao,
    private val setEntryDao: SetEntryDao,
) {
    fun observeWorkouts(): Flow<List<WorkoutEntity>> = workoutDao.observeAll()

    fun observeWorkout(id: Long): Flow<WorkoutEntity?> = workoutDao.observe(id)

    fun observeSets(workoutId: Long): Flow<List<SetEntryEntity>> = setEntryDao.observeForWorkout(workoutId)

    fun observeSetsForExercise(exerciseId: Long): Flow<List<SetEntryEntity>> =
        setEntryDao.observeForExercise(exerciseId)

    suspend fun lastSetFor(exerciseId: Long): SetEntryEntity? = setEntryDao.lastForExercise(exerciseId)

    suspend fun saveWorkout(workout: WorkoutEntity): Long =
        if (workout.id == 0L) workoutDao.insert(workout) else workout.id.also { workoutDao.update(workout) }

    /** Deleting a workout also deletes its sets (foreign key cascade). */
    suspend fun deleteWorkout(workout: WorkoutEntity) = workoutDao.delete(workout)

    suspend fun saveSet(set: SetEntryEntity): Long =
        if (set.id == 0L) setEntryDao.insert(set) else set.id.also { setEntryDao.update(set) }

    suspend fun deleteSet(set: SetEntryEntity) = setEntryDao.delete(set)
}

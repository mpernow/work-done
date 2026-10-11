package com.mpernow.workdone.data.repository

import com.mpernow.workdone.data.db.ExerciseDao
import com.mpernow.workdone.data.db.ExerciseEntity
import kotlinx.coroutines.flow.Flow

class ExerciseRepository(private val exerciseDao: ExerciseDao) {
    fun observeAll(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    fun observe(id: Long): Flow<ExerciseEntity?> = exerciseDao.observe(id)

    suspend fun save(exercise: ExerciseEntity): Long =
        if (exercise.id == 0L) exerciseDao.insert(exercise) else exercise.id.also { exerciseDao.update(exercise) }

    suspend fun delete(exercise: ExerciseEntity) = exerciseDao.delete(exercise)
}

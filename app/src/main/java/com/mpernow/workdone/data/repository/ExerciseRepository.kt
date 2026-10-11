package com.mpernow.workdone.data.repository

import com.mpernow.workdone.data.db.ExerciseDao
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryDao
import kotlinx.coroutines.flow.Flow

sealed interface SaveExerciseResult {
    data class Saved(val id: Long) : SaveExerciseResult
    data object NameTaken : SaveExerciseResult
}

class ExerciseRepository(
    private val exerciseDao: ExerciseDao,
    private val setEntryDao: SetEntryDao,
) {
    fun observeAll(): Flow<List<ExerciseEntity>> = exerciseDao.observeAll()

    fun observeByUsage(): Flow<List<ExerciseEntity>> = exerciseDao.observeByUsage()

    fun observe(id: Long): Flow<ExerciseEntity?> = exerciseDao.observe(id)

    suspend fun get(id: Long): ExerciseEntity? = exerciseDao.get(id)

    suspend fun countSets(exerciseId: Long): Int = setEntryDao.countForExercise(exerciseId)

    /** Names are unique regardless of case, so "squat" and "Squat" can't both exist. */
    suspend fun save(exercise: ExerciseEntity): SaveExerciseResult {
        val existing = exerciseDao.findByName(exercise.name)
        if (existing != null && existing.id != exercise.id) return SaveExerciseResult.NameTaken
        return if (exercise.id == 0L) {
            SaveExerciseResult.Saved(exerciseDao.insert(exercise))
        } else {
            exerciseDao.update(exercise)
            SaveExerciseResult.Saved(exercise.id)
        }
    }

    /** Returns false, and deletes nothing, if the exercise has logged sets. */
    suspend fun delete(exercise: ExerciseEntity): Boolean {
        if (setEntryDao.countForExercise(exercise.id) > 0) return false
        exerciseDao.delete(exercise)
        return true
    }
}

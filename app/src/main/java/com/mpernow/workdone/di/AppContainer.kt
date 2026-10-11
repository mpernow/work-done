package com.mpernow.workdone.di

import android.content.Context
import com.mpernow.workdone.data.db.AppDatabase
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.data.settings.SettingsRepository

/**
 * Hand-written dependency container: one instance per app process, owned by
 * [com.mpernow.workdone.WorkDoneApplication]. ViewModels get their dependencies from here.
 */
class AppContainer(context: Context) {
    private val database = AppDatabase.build(context)

    val exerciseRepository = ExerciseRepository(database.exerciseDao(), database.setEntryDao())
    val workoutRepository = WorkoutRepository(database.workoutDao(), database.setEntryDao())
    val settingsRepository = SettingsRepository(context)
}

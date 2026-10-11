package com.mpernow.workdone.di

import android.content.Context
import android.content.Intent
import com.mpernow.workdone.data.backup.BackupManager
import com.mpernow.workdone.data.db.AppDatabase
import com.mpernow.workdone.data.ocr.PhotoTextReader
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
    val backupManager = BackupManager(context, database, restartApp = { restartApp(context) })
    val photoTextReader = PhotoTextReader(context)
}

/** Relaunches the app in a fresh process, so everything reopens the restored database. */
private fun restartApp(context: Context) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(Intent.makeRestartActivityTask(launchIntent.component))
    Runtime.getRuntime().exit(0)
}

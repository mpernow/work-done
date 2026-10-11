package com.mpernow.workdone.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.mpernow.workdone.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

data class DatabaseSummary(val workouts: Int, val latestWorkout: Instant?)

/** What an import would replace: the backup's contents against the current data. */
data class ImportPreview(val backup: DatabaseSummary, val current: DatabaseSummary)

/** A problem the user should see, worded for them. */
class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Backs up the app's data as a copy of its SQLite database file, and restores such a copy.
 *
 * Import happens in two steps. [prepareImport] copies the chosen file next to the real
 * database and opens it with Room, which upgrades an older backup and rejects anything that
 * isn't a valid Work Done database, all without touching the current data. Only
 * [completeImport] swaps the files, after which the app restarts so nothing keeps using the
 * old database.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
    private val restartApp: () -> Unit,
) {
    private val databaseFile: File get() = context.getDatabasePath(AppDatabase.NAME)
    private val stagedFile: File get() = context.getDatabasePath(STAGED_NAME)

    suspend fun export(destination: Uri) = withContext(Dispatchers.IO) {
        // Move everything from the write-ahead log into the main file, so the file alone is complete.
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val output = context.contentResolver.openOutputStream(destination)
            ?: throw BackupException("Couldn't write to the chosen file.")
        output.use { out -> databaseFile.inputStream().use { it.copyTo(out) } }
    }

    suspend fun prepareImport(source: Uri): ImportPreview = withContext(Dispatchers.IO) {
        discardImport()
        try {
            val input = context.contentResolver.openInputStream(source)
                ?: throw BackupException("Couldn't read the chosen file.")
            input.use { inp -> stagedFile.outputStream().use { inp.copyTo(it) } }
            checkLooksLikeBackup(stagedFile)
            val backup = try {
                summarize(AppDatabase.openFile(context, STAGED_NAME), close = true, checkIntegrity = true)
            } catch (e: Exception) {
                throw BackupException(
                    "This backup can't be read. It may be damaged, or made by a newer version of the app.",
                    e,
                )
            }
            ImportPreview(backup = backup, current = summarize(database, close = false, checkIntegrity = false))
        } catch (e: Exception) {
            discardImport()
            throw e
        }
    }

    /** Replaces the app's data with the backup from [prepareImport] and restarts the app. */
    suspend fun completeImport() = withContext(Dispatchers.IO) {
        if (!stagedFile.exists()) throw BackupException("Choose the backup file again.")
        database.close()
        // The old database's side files would otherwise be applied to the restored one.
        sideFiles(databaseFile).forEach { it.delete() }
        if (!stagedFile.renameTo(databaseFile)) throw BackupException("Couldn't replace the data.")
        discardImport()
        restartApp()
    }

    /** Removes a backup copied by [prepareImport] that won't be imported. */
    fun discardImport() {
        (listOf(stagedFile) + sideFiles(stagedFile)).forEach { it.delete() }
    }

    /**
     * Rejects files that aren't SQLite, or SQLite files that Room didn't create. Room would
     * happily add its tables to any empty-looking database, so this has to be checked first.
     */
    private fun checkLooksLikeBackup(file: File) {
        val notABackup = BackupException("This file isn't a Work Done backup.")
        val header = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        if (!header.contentEquals(SQLITE_HEADER)) throw notABackup
        val looksRight = try {
            SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                val version = db.version
                val hasRoomTable = db.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name IN ('room_master_table', 'workouts')",
                    null,
                ).use { it.count == 2 }
                version >= 1 && hasRoomTable
            }
        } catch (e: Exception) {
            false
        }
        if (!looksRight) throw notABackup
    }

    private suspend fun summarize(db: AppDatabase, close: Boolean, checkIntegrity: Boolean): DatabaseSummary {
        try {
            if (checkIntegrity) {
                val result = db.openHelper.readableDatabase.query("PRAGMA integrity_check").use {
                    it.moveToFirst()
                    it.getString(0)
                }
                check(result == "ok") { "Integrity check failed: $result" }
            }
            return DatabaseSummary(db.workoutDao().count(), db.workoutDao().latestStart())
        } finally {
            if (close) db.close()
        }
    }

    private fun sideFiles(file: File) = listOf("-wal", "-shm", "-journal").map { File(file.path + it) }

    private companion object {
        const val STAGED_NAME = "import-staging.db"
        val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
    }
}

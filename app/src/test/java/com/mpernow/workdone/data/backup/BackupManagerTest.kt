package com.mpernow.workdone.data.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.mpernow.workdone.data.db.AppDatabase
import com.mpernow.workdone.data.db.WorkoutEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: AppDatabase
    private lateinit var manager: BackupManager
    private var restarted = false

    @Before
    fun setUp() {
        database = AppDatabase.build(context)
        manager = BackupManager(context, database, restartApp = { restarted = true })
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    private fun workout(day: Int) = WorkoutEntity(
        startedAt = Instant.parse("2026-10-%02dT10:00:00Z".format(day)),
        finishedAt = Instant.parse("2026-10-%02dT11:00:00Z".format(day)),
    )

    private fun tempFile(name: String) = File(context.cacheDir, name).also { it.delete() }

    @Test
    fun exportThenImportRestoresTheExportedData() = runTest {
        database.workoutDao().insert(workout(1))
        val backup = tempFile("backup.db")
        manager.export(Uri.fromFile(backup))
        database.workoutDao().insert(workout(5)) // made after the backup, so lost on import

        val preview = manager.prepareImport(Uri.fromFile(backup))
        assertEquals(DatabaseSummary(1, Instant.parse("2026-10-01T10:00:00Z")), preview.backup)
        assertEquals(2, preview.current.workouts)

        manager.completeImport()
        assertTrue(restarted)
        val restored = AppDatabase.build(context)
        assertEquals(1, restored.workoutDao().count())
        restored.close()
    }

    @Test
    fun anOlderVersionBackupIsUpgradedOnImport() = runTest {
        migrationHelper.createDatabase("old-backup.db", 1).use { db ->
            db.execSQL("INSERT INTO workouts (id, startedAt, notes) VALUES (1, 1759312800000, 'Legs')")
        }

        val preview = manager.prepareImport(Uri.fromFile(context.getDatabasePath("old-backup.db")))

        assertEquals(1, preview.backup.workouts)
    }

    @Test
    fun aFileThatIsNotSqliteIsRejected() = runTest {
        val file = tempFile("notes.txt").apply { writeText("not a database at all") }
        assertRejected(file)
    }

    @Test
    fun anotherAppsDatabaseIsRejected() = runTest {
        val file = tempFile("other.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE contacts (id INTEGER PRIMARY KEY, name TEXT)")
            db.version = 3
        }
        assertRejected(file)
    }

    private suspend fun assertRejected(file: File) {
        database.workoutDao().insert(workout(1))
        try {
            manager.prepareImport(Uri.fromFile(file))
            fail("Expected the file to be rejected")
        } catch (e: BackupException) {
            assertEquals("This file isn't a Work Done backup.", e.message)
        }
        assertFalse("staged copy is cleaned up", context.getDatabasePath("import-staging.db").exists())
        assertEquals("current data is untouched", 1, database.workoutDao().count())
    }
}

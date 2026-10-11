package com.mpernow.workdone.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2KeepsWorkoutsAndLeavesThemUnfinished() {
        helper.createDatabase(DB_NAME, 1).use { db ->
            db.execSQL("INSERT INTO workouts (id, startedAt, notes) VALUES (1, 1759312800000, 'Legs')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 2, true).use { db ->
            db.query("SELECT notes, finishedAt FROM workouts WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Legs", cursor.getString(0))
                assertTrue(cursor.isNull(1))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}

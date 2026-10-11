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

    @Test
    fun migrate2To3DropsCategoriesButKeepsExercises() {
        helper.createDatabase(DB_NAME, 2).use { db ->
            db.execSQL("INSERT INTO exercises (id, name, category, trackingType) VALUES (1, 'Squat', 'LEGS', 'WEIGHT_REPS')")
        }

        helper.runMigrationsAndValidate(DB_NAME, 3, true).use { db ->
            db.query("SELECT name, trackingType FROM exercises WHERE id = 1").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("Squat", cursor.getString(0))
                assertEquals("WEIGHT_REPS", cursor.getString(1))
                assertEquals(-1, cursor.getColumnIndex("category"))
            }
        }
    }

    @Test
    fun migrate3To4AddsEmptyIntervalFields() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL("INSERT INTO exercises (id, name, trackingType) VALUES (1, 'Squat', 'WEIGHT_REPS')")
            db.execSQL("INSERT INTO workouts (id, startedAt, notes) VALUES (1, 1759312800000, '')")
            db.execSQL("INSERT INTO set_entries (workoutId, exerciseId, position, reps, weightKg) VALUES (1, 1, 0, 5, 100.0)")
        }

        helper.runMigrationsAndValidate(DB_NAME, 4, true).use { db ->
            db.query("SELECT reps, restSeconds, splitSeconds, resistance FROM set_entries").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
                assertTrue(cursor.isNull(1) && cursor.isNull(2) && cursor.isNull(3))
            }
        }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}

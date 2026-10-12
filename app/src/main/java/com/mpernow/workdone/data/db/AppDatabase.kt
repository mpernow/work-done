package com.mpernow.workdone.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.DeleteColumn
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mpernow.workdone.domain.TrackingType

@Database(
    entities = [ExerciseEntity::class, WorkoutEntity::class, SetEntryEntity::class],
    version = 6,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // workouts.finishedAt
        AutoMigration(from = 2, to = 3, spec = DropExerciseCategory::class),
        AutoMigration(from = 3, to = 4), // set_entries.restSeconds, splitSeconds, resistance
        AutoMigration(from = 4, to = 5), // set_entries.strokeRate
        AutoMigration(from = 5, to = 6), // set_entries.entry
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exerciseDao(): ExerciseDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun setEntryDao(): SetEntryDao

    companion object {
        /** File name of the app's database, inside the app's databases folder. */
        const val NAME = "workdone.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .addCallback(SeedExercises)
                .build()

        /**
         * Opens another database file in the databases folder, such as a backup being checked
         * before import. Opening it upgrades it to the current version and fails if it isn't a
         * valid Work Done database. No starter exercises are added, and no WAL file is left behind.
         */
        fun openFile(context: Context, name: String): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
    }
}

/** Fills the library with common exercises the first time the database is created. */
private object SeedExercises : RoomDatabase.Callback() {
    private val exercises = listOf(
        "Bench press" to TrackingType.WEIGHT_REPS,
        "Push-up" to TrackingType.REPS,
        "Deadlift" to TrackingType.WEIGHT_REPS,
        "Barbell row" to TrackingType.WEIGHT_REPS,
        "Pull-up" to TrackingType.REPS,
        "Overhead press" to TrackingType.WEIGHT_REPS,
        "Biceps curl" to TrackingType.WEIGHT_REPS,
        "Squat" to TrackingType.WEIGHT_REPS,
        "Plank" to TrackingType.DURATION,
        "Running" to TrackingType.DISTANCE_TIME,
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        exercises.forEach { (name, trackingType) ->
            db.execSQL(
                "INSERT INTO exercises (name, trackingType) VALUES (?, ?)",
                arrayOf(name, trackingType.name),
            )
        }
    }
}

/** Version 3 drops exercise categories, which turned out not to be needed. */
@DeleteColumn(tableName = "exercises", columnName = "category")
class DropExerciseCategory : AutoMigrationSpec

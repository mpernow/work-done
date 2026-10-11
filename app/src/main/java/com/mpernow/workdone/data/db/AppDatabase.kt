package com.mpernow.workdone.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mpernow.workdone.domain.ExerciseCategory
import com.mpernow.workdone.domain.TrackingType

@Database(
    entities = [ExerciseEntity::class, WorkoutEntity::class, SetEntryEntity::class],
    version = 2,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // workouts.finishedAt
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
        Triple("Bench press", ExerciseCategory.CHEST, TrackingType.WEIGHT_REPS),
        Triple("Push-up", ExerciseCategory.CHEST, TrackingType.REPS),
        Triple("Deadlift", ExerciseCategory.BACK, TrackingType.WEIGHT_REPS),
        Triple("Barbell row", ExerciseCategory.BACK, TrackingType.WEIGHT_REPS),
        Triple("Pull-up", ExerciseCategory.BACK, TrackingType.REPS),
        Triple("Overhead press", ExerciseCategory.SHOULDERS, TrackingType.WEIGHT_REPS),
        Triple("Biceps curl", ExerciseCategory.ARMS, TrackingType.WEIGHT_REPS),
        Triple("Squat", ExerciseCategory.LEGS, TrackingType.WEIGHT_REPS),
        Triple("Plank", ExerciseCategory.CORE, TrackingType.DURATION),
        Triple("Running", ExerciseCategory.CARDIO, TrackingType.DISTANCE_TIME),
    )

    override fun onCreate(db: SupportSQLiteDatabase) {
        exercises.forEach { (name, category, trackingType) ->
            db.execSQL(
                "INSERT INTO exercises (name, category, trackingType) VALUES (?, ?, ?)",
                arrayOf(name, category.name, trackingType.name),
            )
        }
    }
}

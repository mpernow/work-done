package com.mpernow.workdone.data.db

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mpernow.workdone.domain.TrackingType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: AppDatabase
    private lateinit var exercises: ExerciseDao
    private lateinit var workouts: WorkoutDao
    private lateinit var sets: SetEntryDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        exercises = db.exerciseDao()
        workouts = db.workoutDao()
        sets = db.setEntryDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun squat() =
        exercises.insert(ExerciseEntity(name = "Squat", trackingType = TrackingType.WEIGHT_REPS))

    private suspend fun addSet(workoutId: Long, exerciseId: Long, weightKg: Double, reps: Int = 5) =
        sets.insertAtEnd(workoutId, exerciseId, reps, weightKg, durationSeconds = null, distanceMeters = null)

    @Test
    fun insertAtEndNumbersSetsInOrder() = runTest {
        val exerciseId = squat()
        val workoutId = workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-01T10:00:00Z")))
        repeat(3) { addSet(workoutId, exerciseId, 100.0) }

        assertEquals(listOf(0, 1, 2), sets.observeForWorkout(workoutId).first().map { it.position })
    }

    @Test
    fun lastForExerciseIsTheLatestSetOfTheLatestWorkout() = runTest {
        val exerciseId = squat()
        val older = workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-01T10:00:00Z")))
        val newer = workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-03T10:00:00Z")))
        // Logged out of order, to check the ordering comes from the workout date, not insertion.
        addSet(newer, exerciseId, 105.0)
        addSet(newer, exerciseId, 110.0)
        addSet(older, exerciseId, 120.0)

        assertEquals(110.0, sets.lastForExercise(exerciseId)?.weightKg)
    }

    @Test
    fun activeWorkoutIsTheUnfinishedOne() = runTest {
        workouts.insert(
            WorkoutEntity(startedAt = Instant.parse("2026-10-01T10:00:00Z"), finishedAt = Instant.parse("2026-10-01T11:00:00Z")),
        )
        assertNull(workouts.getActive())

        val activeId = workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-02T10:00:00Z")))
        assertEquals(activeId, workouts.observeActive().first()?.id)
    }

    @Test
    fun deletingAWorkoutDeletesItsSets() = runTest {
        val exerciseId = squat()
        val workoutId = workouts.insert(WorkoutEntity(startedAt = Instant.now()))
        addSet(workoutId, exerciseId, 100.0)

        workouts.delete(workouts.get(workoutId)!!)

        assertEquals(0, sets.countForExercise(exerciseId))
    }

    @Test(expected = SQLiteConstraintException::class)
    fun anExerciseWithSetsCannotBeDeleted() = runTest {
        val exerciseId = squat()
        val workoutId = workouts.insert(WorkoutEntity(startedAt = Instant.now()))
        addSet(workoutId, exerciseId, 100.0)

        exercises.delete(exercises.get(exerciseId)!!)
    }

    @Test
    fun findByNameIgnoresCase() = runTest {
        val exerciseId = squat()
        assertEquals(exerciseId, exercises.findByName("SQUAT")?.id)
    }

    @Test
    fun historyListsOnlyFinishedWorkouts() = runTest {
        val finished = workouts.insert(
            WorkoutEntity(startedAt = Instant.parse("2026-10-01T10:00:00Z"), finishedAt = Instant.parse("2026-10-01T11:00:00Z")),
        )
        workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-02T10:00:00Z")))

        assertEquals(listOf(finished), workouts.observeFinished().first().map { it.id })
    }

    @Test
    fun exerciseSummariesCountSetsPerExerciseInEachWorkout() = runTest {
        val squatId = squat()
        val benchId = exercises.insert(
            ExerciseEntity(name = "Bench press", trackingType = TrackingType.WEIGHT_REPS),
        )
        val workoutId = workouts.insert(WorkoutEntity(startedAt = Instant.now()))
        addSet(workoutId, benchId, 60.0)
        addSet(workoutId, squatId, 100.0)
        addSet(workoutId, squatId, 100.0)
        addSet(workoutId, benchId, 60.0)

        val summaries = sets.observeExerciseSummaries().first().sortedBy { it.firstPosition }

        assertEquals(
            listOf(
                WorkoutExerciseSummary(workoutId, "Bench press", setCount = 2, firstPosition = 0),
                WorkoutExerciseSummary(workoutId, "Squat", setCount = 2, firstPosition = 1),
            ),
            summaries,
        )
    }

    @Test
    fun progressQueryOnlyIncludesFinishedWorkouts() = runTest {
        val exerciseId = squat()
        val finished = workouts.insert(
            WorkoutEntity(startedAt = Instant.parse("2026-10-01T10:00:00Z"), finishedAt = Instant.parse("2026-10-01T11:00:00Z")),
        )
        val active = workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-02T10:00:00Z")))
        addSet(finished, exerciseId, 100.0)
        addSet(active, exerciseId, 120.0)

        val rows = sets.observeFinishedWithDates(exerciseId).first()

        assertEquals(listOf(100.0), rows.map { it.set.weightKg })
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), rows.single().startedAt)
    }

    @Test
    fun workoutTotalsSumVolumeAndTreatUnweightedSetsAsZero() = runTest {
        val squatId = squat()
        val plankId = exercises.insert(
            ExerciseEntity(name = "Plank", trackingType = TrackingType.DURATION),
        )
        val workoutId = workouts.insert(
            WorkoutEntity(startedAt = Instant.parse("2026-10-01T10:00:00Z"), finishedAt = Instant.parse("2026-10-01T11:00:00Z")),
        )
        addSet(workoutId, squatId, 100.0, reps = 5)
        addSet(workoutId, squatId, 100.0, reps = 3)
        sets.insertAtEnd(workoutId, plankId, reps = null, weightKg = null, durationSeconds = 60, distanceMeters = null)

        val totals = sets.observeWorkoutTotals().first().single()

        assertEquals(workoutId, totals.workoutId)
        assertEquals(3, totals.setCount)
        assertEquals(800.0, totals.volumeKg, 1e-9) // the plank adds a set but no volume
    }

    @Test
    fun byUsageCountsWorkoutsNotSetsThenSortsByName() = runTest {
        val squat = squat()
        val bench = exercises.insert(ExerciseEntity(name = "Bench press", trackingType = TrackingType.WEIGHT_REPS))
        exercises.insert(ExerciseEntity(name = "Deadlift", trackingType = TrackingType.WEIGHT_REPS)) // never used
        exercises.insert(ExerciseEntity(name = "curl", trackingType = TrackingType.WEIGHT_REPS)) // never used, lower case
        val first = workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-01T10:00:00Z")))
        val second = workouts.insert(WorkoutEntity(startedAt = Instant.parse("2026-10-03T10:00:00Z")))
        // Squat: many sets in one workout. Bench: one set in each of two workouts.
        repeat(5) { addSet(first, squat, 100.0) }
        addSet(first, bench, 60.0)
        addSet(second, bench, 60.0)

        assertEquals(
            listOf("Bench press", "Squat", "curl", "Deadlift"),
            exercises.observeByUsage().first().map { it.name },
        )
    }
}

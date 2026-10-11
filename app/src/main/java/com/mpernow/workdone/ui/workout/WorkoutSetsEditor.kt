package com.mpernow.workdone.ui.workout

import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.toValues
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.SetValues
import com.mpernow.workdone.domain.WeightUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One exercise in a workout: its sets so far and the input for the next one. */
data class ExerciseBlock(
    val exercise: ExerciseEntity,
    val sets: List<SetEntryEntity>,
    val draft: SetDraft,
    val canAddSet: Boolean,
)

data class WorkoutSets(val blocks: List<ExerciseBlock>, val weightUnit: WeightUnit)

/**
 * The set-editing logic shared by the Log screen and the workout detail screen. It lives in
 * a ViewModel and uses that ViewModel's [scope].
 */
class WorkoutSetsEditor(
    private val scope: CoroutineScope,
    private val workoutRepository: WorkoutRepository,
    private val settingsRepository: SettingsRepository,
    private val exercises: Flow<List<ExerciseEntity>>,
) {
    /** Exercises added to the workout that have no sets yet; these only live in memory. */
    private val addedExerciseIds = MutableStateFlow<List<Long>>(emptyList())
    private val drafts = MutableStateFlow<Map<Long, SetDraft>>(emptyMap())

    fun observe(workoutId: Long): Flow<WorkoutSets> = combine(
        workoutRepository.observeSets(workoutId),
        exercises,
        addedExerciseIds,
        drafts,
        settingsRepository.weightUnit,
    ) { sets, exercises, added, drafts, unit ->
        val exercisesById = exercises.associateBy { it.id }
        val setsByExercise = sets.groupBy { it.exerciseId }
        // Exercises appear in the order they were first used, then any still without sets.
        val order = (sets.map { it.exerciseId } + added).distinct()
        val blocks = order.mapNotNull { id ->
            val exercise = exercisesById[id] ?: return@mapNotNull null
            val exerciseSets = setsByExercise[id].orEmpty()
            // After process death the drafts are gone; fall back to the last set logged here.
            val draft = drafts[id]
                ?: exerciseSets.lastOrNull()?.let { SetInput.draftFrom(it.toValues(), unit) }
                ?: SetDraft()
            ExerciseBlock(
                exercise = exercise,
                sets = exerciseSets,
                draft = draft,
                canAddSet = SetInput.parse(draft, exercise.trackingType, unit) != null,
            )
        }
        WorkoutSets(blocks, unit)
    }

    /** Adds an exercise and pre-fills its input with the last set ever logged for it. */
    fun addExercise(exerciseId: Long) {
        addedExerciseIds.update { if (exerciseId in it) it else it + exerciseId }
        if (exerciseId in drafts.value) return
        scope.launch {
            val last = workoutRepository.lastSetFor(exerciseId) ?: return@launch
            val unit = settingsRepository.weightUnit.first()
            drafts.update { if (exerciseId in it) it else it + (exerciseId to SetInput.draftFrom(last.toValues(), unit)) }
        }
    }

    fun updateDraft(exerciseId: Long, draft: SetDraft) {
        drafts.update { it + (exerciseId to draft) }
    }

    /**
     * Logs the block's current input as a new set, keeping the input ready for the next one.
     * Returns false, without saving anything, if the input isn't valid.
     */
    suspend fun addSet(workoutId: Long, block: ExerciseBlock, unit: WeightUnit): Boolean {
        val values = SetInput.parse(block.draft, block.exercise.trackingType, unit) ?: return false
        drafts.update { it + (block.exercise.id to block.draft) }
        workoutRepository.addSet(workoutId, block.exercise.id, values)
        return true
    }

    fun updateSet(set: SetEntryEntity, values: SetValues) {
        scope.launch {
            workoutRepository.saveSet(
                set.copy(
                    reps = values.reps,
                    weightKg = values.weightKg,
                    durationSeconds = values.durationSeconds,
                    distanceMeters = values.distanceMeters,
                ),
            )
        }
    }

    fun deleteSet(set: SetEntryEntity) {
        scope.launch { workoutRepository.deleteSet(set) }
    }

    fun reset() {
        addedExerciseIds.value = emptyList()
        drafts.value = emptyMap()
    }
}

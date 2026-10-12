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

/** Identifies one entry of an exercise in a workout; see [SetEntryEntity.entry]. */
data class BlockKey(val exerciseId: Long, val entry: Int) {
    /** A key for lazy lists, which must be a type that can be saved in a Bundle. */
    val listKey: String get() = "$exerciseId-$entry"
}

/** One entry of an exercise in a workout: its sets so far and the input for the next one. */
data class ExerciseBlock(
    val key: BlockKey,
    val exercise: ExerciseEntity,
    /** Counts the entries when the exercise is in the workout more than once, from 1; otherwise null. */
    val number: Int?,
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
    /** Entries added to the workout that have no sets yet; these only live in memory. */
    private val addedBlocks = MutableStateFlow<List<BlockKey>>(emptyList())
    private val drafts = MutableStateFlow<Map<BlockKey, SetDraft>>(emptyMap())

    fun observe(workoutId: Long): Flow<WorkoutSets> = combine(
        workoutRepository.observeSets(workoutId),
        exercises,
        addedBlocks,
        drafts,
        settingsRepository.weightUnit,
    ) { sets, exercises, added, drafts, unit ->
        val exercisesById = exercises.associateBy { it.id }
        val setsByBlock = sets.groupBy { BlockKey(it.exerciseId, it.entry) }
        // Entries appear in the order they were first used, then any still without sets.
        val order = (setsByBlock.keys + added).distinct().filter { it.exerciseId in exercisesById }
        val entryCounts = order.groupingBy { it.exerciseId }.eachCount()
        val blocks = order.map { key ->
            val exercise = exercisesById.getValue(key.exerciseId)
            val exerciseSets = setsByBlock[key].orEmpty()
            // After process death the drafts are gone; fall back to the last set logged here.
            val draft = drafts[key]
                ?: exerciseSets.lastOrNull()?.let { SetInput.draftFrom(it.toValues(), unit) }
                ?: SetDraft()
            ExerciseBlock(
                key = key,
                exercise = exercise,
                number = if (entryCounts.getValue(key.exerciseId) > 1) {
                    order.filter { it.exerciseId == key.exerciseId }.indexOf(key) + 1
                } else {
                    null
                },
                sets = exerciseSets,
                draft = draft,
                canAddSet = SetInput.parse(draft, exercise.trackingType, unit) != null,
            )
        }
        WorkoutSets(blocks, unit)
    }

    /**
     * Adds an entry of an exercise, after any already in [blocks], and pre-fills its input with
     * the last set ever logged for it. If the exercise has an entry without sets, that one is
     * used instead of adding another empty one.
     */
    fun addExercise(exerciseId: Long, blocks: List<ExerciseBlock>) {
        val existing = blocks.filter { it.exercise.id == exerciseId }
        if (existing.any { it.sets.isEmpty() }) return
        val key = BlockKey(exerciseId, (existing.maxOfOrNull { it.key.entry } ?: -1) + 1)
        addedBlocks.update { it + key }
        if (key in drafts.value) return
        scope.launch {
            val last = workoutRepository.lastSetFor(exerciseId) ?: return@launch
            val unit = settingsRepository.weightUnit.first()
            drafts.update { if (key in it) it else it + (key to SetInput.draftFrom(last.toValues(), unit)) }
        }
    }

    fun updateDraft(key: BlockKey, draft: SetDraft) {
        drafts.update { it + (key to draft) }
    }

    /**
     * Logs the block's current input as a new set, keeping the input ready for the next one.
     * Returns false, without saving anything, if the input isn't valid.
     */
    suspend fun addSet(workoutId: Long, block: ExerciseBlock, unit: WeightUnit): Boolean {
        val values = SetInput.parse(block.draft, block.exercise.trackingType, unit) ?: return false
        drafts.update { it + (block.key to block.draft) }
        workoutRepository.addSet(workoutId, block.exercise.id, block.key.entry, values)
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
                    restSeconds = values.restSeconds,
                    splitSeconds = values.splitSeconds,
                    resistance = values.resistance,
                    strokeRate = values.strokeRate,
                ),
            )
        }
    }

    fun deleteSet(set: SetEntryEntity) {
        scope.launch { workoutRepository.deleteSet(set) }
    }

    fun reset() {
        addedBlocks.value = emptyList()
        drafts.value = emptyMap()
    }
}

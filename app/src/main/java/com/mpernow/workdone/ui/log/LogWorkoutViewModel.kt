package com.mpernow.workdone.ui.log

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.WorkoutEntity
import com.mpernow.workdone.data.db.toValues
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.WeightUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/** One exercise in the workout being logged: its sets so far and the input for the next one. */
data class ExerciseBlock(
    val exercise: ExerciseEntity,
    val sets: List<SetEntryEntity>,
    val draft: SetDraft,
    val canAddSet: Boolean,
)

sealed interface LogUiState {
    data object Loading : LogUiState
    data object Idle : LogUiState
    data class Active(
        val workout: WorkoutEntity,
        val blocks: List<ExerciseBlock>,
        val weightUnit: WeightUnit,
    ) : LogUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class LogWorkoutViewModel(
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Exercises added to the workout that have no sets yet; these only live in memory. */
    private val addedExerciseIds = MutableStateFlow<List<Long>>(emptyList())
    private val drafts = MutableStateFlow<Map<Long, SetDraft>>(emptyMap())

    private val _restEndsAt = MutableStateFlow<Long?>(null)

    /** When the rest timer runs out, in [SystemClock.elapsedRealtime] milliseconds; null when not running. */
    val restEndsAt: StateFlow<Long?> = _restEndsAt.asStateFlow()

    val exercises: StateFlow<List<ExerciseEntity>> = exerciseRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val uiState: StateFlow<LogUiState> = workoutRepository.observeActiveWorkout()
        .flatMapLatest { workout ->
            if (workout == null) {
                flowOf(LogUiState.Idle)
            } else {
                combine(
                    workoutRepository.observeSets(workout.id),
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
                    LogUiState.Active(workout, blocks, unit)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState.Loading)

    fun startWorkout() {
        viewModelScope.launch { workoutRepository.startWorkout(Instant.now()) }
    }

    /** Adds an exercise and pre-fills its input with the last set ever logged for it. */
    fun addExercise(exerciseId: Long) {
        addedExerciseIds.update { if (exerciseId in it) it else it + exerciseId }
        if (exerciseId in drafts.value) return
        viewModelScope.launch {
            val last = workoutRepository.lastSetFor(exerciseId) ?: return@launch
            val unit = settingsRepository.weightUnit.first()
            drafts.update { if (exerciseId in it) it else it + (exerciseId to SetInput.draftFrom(last.toValues(), unit)) }
        }
    }

    fun updateDraft(exerciseId: Long, draft: SetDraft) {
        drafts.update { it + (exerciseId to draft) }
    }

    /** Logs the exercise's current input as a new set. The input is kept, ready for the next set. */
    fun addSet(exerciseId: Long) {
        val state = uiState.value as? LogUiState.Active ?: return
        val block = state.blocks.firstOrNull { it.exercise.id == exerciseId } ?: return
        val values = SetInput.parse(block.draft, block.exercise.trackingType, state.weightUnit) ?: return
        drafts.update { it + (exerciseId to block.draft) }
        viewModelScope.launch {
            workoutRepository.addSet(state.workout.id, exerciseId, values)
            val restSeconds = settingsRepository.restTimerSeconds.first()
            if (restSeconds > 0) _restEndsAt.value = SystemClock.elapsedRealtime() + restSeconds * 1000L
        }
    }

    fun deleteSet(set: SetEntryEntity) {
        viewModelScope.launch { workoutRepository.deleteSet(set) }
    }

    fun extendRest(seconds: Int) {
        _restEndsAt.update { it?.plus(seconds * 1000L) }
    }

    fun stopRest() {
        _restEndsAt.value = null
    }

    fun finishWorkout(notes: String) {
        val state = uiState.value as? LogUiState.Active ?: return
        viewModelScope.launch {
            workoutRepository.finishWorkout(state.workout.id, notes, Instant.now())
            reset()
        }
    }

    fun discardWorkout() {
        val state = uiState.value as? LogUiState.Active ?: return
        viewModelScope.launch {
            workoutRepository.deleteWorkout(state.workout)
            reset()
        }
    }

    private fun reset() {
        addedExerciseIds.value = emptyList()
        drafts.value = emptyMap()
        _restEndsAt.value = null
    }
}

package com.mpernow.workdone.ui.log

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.db.SetEntryEntity
import com.mpernow.workdone.data.db.WorkoutEntity
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetValues
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.domain.WorkoutDates
import com.mpernow.workdone.domain.usesSplits
import com.mpernow.workdone.ui.workout.BlockKey
import com.mpernow.workdone.ui.workout.ExerciseBlock
import com.mpernow.workdone.ui.workout.WorkoutSetsEditor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

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
    private val _restEndsAt = MutableStateFlow<Long?>(null)

    /** When the rest timer runs out, in [SystemClock.elapsedRealtime] milliseconds; null when not running. */
    val restEndsAt: StateFlow<Long?> = _restEndsAt.asStateFlow()

    /** Most used first, which is the order the exercise picker shows them in. */
    val exercises: StateFlow<List<ExerciseEntity>> = exerciseRepository.observeByUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val editor = WorkoutSetsEditor(viewModelScope, workoutRepository, settingsRepository, exercises)

    val uiState: StateFlow<LogUiState> = workoutRepository.observeActiveWorkout()
        .flatMapLatest { workout ->
            if (workout == null) {
                flowOf(LogUiState.Idle)
            } else {
                editor.observe(workout.id).map { LogUiState.Active(workout, it.blocks, it.weightUnit) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogUiState.Loading)

    fun startWorkout() {
        viewModelScope.launch { workoutRepository.startWorkout(Instant.now()) }
    }

    /** Moves the workout's start, e.g. to log a workout done earlier. */
    fun setStartTime(startedAt: Instant) {
        val state = uiState.value as? LogUiState.Active ?: return
        viewModelScope.launch { workoutRepository.saveWorkout(state.workout.copy(startedAt = startedAt)) }
    }

    /** Adds the exercise, or another entry of it if it's already in the workout. */
    fun addExercise(exerciseId: Long) {
        val state = uiState.value as? LogUiState.Active ?: return
        editor.addExercise(exerciseId, state.blocks)
    }

    fun updateDraft(key: BlockKey, draft: SetDraft) = editor.updateDraft(key, draft)

    /** Logs the exercise's input as a set and starts the rest timer, except for backdated workouts and rowing-machine types. */
    fun addSet(key: BlockKey) {
        val state = uiState.value as? LogUiState.Active ?: return
        val block = state.blocks.firstOrNull { it.key == key } ?: return
        viewModelScope.launch {
            if (!editor.addSet(state.workout.id, block, state.weightUnit)) return@launch
            if (WorkoutDates.isBackdated(state.workout.startedAt, Instant.now())) return@launch
            // Rowing machines time their own rest.
            if (block.exercise.trackingType.usesSplits) return@launch
            val restSeconds = settingsRepository.restTimerSeconds.first()
            if (restSeconds > 0) _restEndsAt.value = SystemClock.elapsedRealtime() + restSeconds * 1000L
        }
    }

    fun updateSet(set: SetEntryEntity, values: SetValues) = editor.updateSet(set, values)

    fun deleteSet(set: SetEntryEntity) = editor.deleteSet(set)

    fun extendRest(seconds: Int) {
        _restEndsAt.update { it?.plus(seconds * 1000L) }
    }

    fun stopRest() {
        _restEndsAt.value = null
    }

    fun finishWorkout(notes: String, finishedAt: Instant) {
        val state = uiState.value as? LogUiState.Active ?: return
        viewModelScope.launch {
            workoutRepository.finishWorkout(state.workout.id, notes, finishedAt)
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
        editor.reset()
        _restEndsAt.value = null
    }
}

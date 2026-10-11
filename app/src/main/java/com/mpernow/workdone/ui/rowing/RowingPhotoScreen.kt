package com.mpernow.workdone.ui.rowing

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.db.ExerciseEntity
import com.mpernow.workdone.data.ocr.PhotoTextReader
import com.mpernow.workdone.data.repository.ExerciseRepository
import com.mpernow.workdone.data.repository.WorkoutRepository
import com.mpernow.workdone.domain.DigitTime
import com.mpernow.workdone.domain.Pm5Import
import com.mpernow.workdone.domain.Pm5Parser
import com.mpernow.workdone.domain.Pm5Workout
import com.mpernow.workdone.domain.SetDraft
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.TrackingType
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.appViewModelFactory
import com.mpernow.workdone.ui.workout.DigitFormat
import com.mpernow.workdone.ui.workout.DigitTimeField
import com.mpernow.workdone.ui.workout.NumberField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

/** One interval or split as read from the photo, editable before it's saved. */
data class ImportRow(val draft: SetDraft, val problem: String?)

sealed interface RowingPhotoUi {
    data object ChoosePhoto : RowingPhotoUi
    data class Reading(val photo: Uri) : RowingPhotoUi
    data class Failed(val photo: Uri, val message: String) : RowingPhotoUi
    data class Review(
        val photo: Uri,
        val workout: Pm5Workout,
        val trackingType: TrackingType,
        val rows: List<ImportRow>,
        val problems: List<String>,
        /** Rest between intervals, as m:ss digits; shared by every row. */
        val rest: String,
        /** Not on the screen, so typed once for every row. */
        val resistance: String,
        val exerciseId: Long?,
        val photoDate: LocalDate?,
        val workoutDate: LocalDate,
        val useDate: Boolean,
    ) : RowingPhotoUi

    data object Saved : RowingPhotoUi
}

class RowingPhotoViewModel(
    private val workoutRepository: WorkoutRepository,
    exerciseRepository: ExerciseRepository,
    private val textReader: PhotoTextReader,
    private val workoutId: Long,
) : ViewModel() {
    val exercises: StateFlow<List<ExerciseEntity>> = exerciseRepository.observeByUsage()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow<RowingPhotoUi>(RowingPhotoUi.ChoosePhoto)
    val state: StateFlow<RowingPhotoUi> = _state.asStateFlow()

    fun readPhoto(photo: Uri) {
        _state.value = RowingPhotoUi.Reading(photo)
        viewModelScope.launch {
            _state.value = try {
                val result = Pm5Parser.parse(textReader.read(photo))
                val workout = result.workout
                if (workout == null || result.rows.isEmpty()) {
                    RowingPhotoUi.Failed(
                        photo,
                        "Couldn't read a results table. Take the photo straight on, with the whole " +
                            "\"View Detail\" screen in view and as little glare as possible.",
                    )
                } else {
                    val values = Pm5Import.toSetValues(workout, result.rows)
                    val workoutStart = workoutRepository.getWorkout(workoutId)?.startedAt
                    RowingPhotoUi.Review(
                        photo = photo,
                        workout = workout,
                        trackingType = Pm5Import.trackingType(workout),
                        rows = values.zip(result.rows) { value, row ->
                            ImportRow(SetInput.draftFrom(value, WeightUnit.KG), row.problem)
                        },
                        problems = result.problems,
                        rest = Pm5Import.restSeconds(workout)?.let(DigitTime::minSecDigits).orEmpty(),
                        resistance = "",
                        exerciseId = null,
                        photoDate = result.date,
                        workoutDate = (workoutStart ?: java.time.Instant.now()).atZone(ZoneId.systemDefault()).toLocalDate(),
                        useDate = true,
                    )
                }
            } catch (e: Exception) {
                RowingPhotoUi.Failed(photo, "Couldn't read the photo: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun choosePhotoAgain() {
        _state.value = RowingPhotoUi.ChoosePhoto
    }

    private fun updateReview(change: (RowingPhotoUi.Review) -> RowingPhotoUi.Review) {
        _state.update { if (it is RowingPhotoUi.Review) change(it) else it }
    }

    /** Editing a row clears its warning: the user has checked it. */
    fun updateRow(index: Int, draft: SetDraft) = updateReview { review ->
        review.copy(rows = review.rows.mapIndexed { i, row -> if (i == index) ImportRow(draft, null) else row })
    }

    fun setRest(digits: String) = updateReview { it.copy(rest = digits) }

    fun setResistance(text: String) = updateReview { it.copy(resistance = text) }

    fun selectExercise(id: Long) = updateReview { it.copy(exerciseId = id) }

    fun setUseDate(use: Boolean) = updateReview { it.copy(useDate = use) }

    fun save(exerciseId: Long) {
        val review = _state.value as? RowingPhotoUi.Review ?: return
        val values = review.rows.map { parse(review, it.draft) ?: return }
        viewModelScope.launch {
            values.forEach { workoutRepository.addSet(workoutId, exerciseId, it) }
            val photoDate = review.photoDate
            if (review.useDate && photoDate != null && photoDate != review.workoutDate) moveWorkoutTo(photoDate)
            _state.value = RowingPhotoUi.Saved
        }
    }

    /** Keeps the workout's time of day and duration, only changing the day. */
    private suspend fun moveWorkoutTo(date: LocalDate) {
        val workout = workoutRepository.getWorkout(workoutId) ?: return
        val zone = ZoneId.systemDefault()
        val start = workout.startedAt.atZone(zone)
        val newStart = date.atTime(start.toLocalTime()).atZone(zone).toInstant()
        val shift = Duration.between(workout.startedAt, newStart)
        workoutRepository.saveWorkout(workout.copy(startedAt = newStart, finishedAt = workout.finishedAt?.plus(shift)))
    }

    companion object {
        fun parse(review: RowingPhotoUi.Review, draft: SetDraft) =
            SetInput.parse(draft.copy(rest = review.rest, resistance = review.resistance), review.trackingType, WeightUnit.KG)
    }
}

private val dateFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")

@Composable
fun RowingPhotoScreen(
    workoutId: Long,
    onDone: () -> Unit,
    onCreateExercise: (TrackingType) -> Unit,
    onNavigateUp: () -> Unit,
    viewModel: RowingPhotoViewModel = viewModel(
        factory = appViewModelFactory {
            RowingPhotoViewModel(it.workoutRepository, it.exerciseRepository, it.photoTextReader, workoutId)
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val exercises by viewModel.exercises.collectAsStateWithLifecycle()
    LaunchedEffect(state) { if (state == RowingPhotoUi.Saved) onDone() }

    when (val current = state) {
        RowingPhotoUi.ChoosePhoto, RowingPhotoUi.Saved -> ChoosePhoto(viewModel::readPhoto, onNavigateUp)
        is RowingPhotoUi.Reading -> AppScaffold("Rowing machine photo", onNavigateUp = onNavigateUp) { padding ->
            Column(
                Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text("Reading the screen…", modifier = Modifier.padding(top = 16.dp))
            }
        }
        is RowingPhotoUi.Failed -> AppScaffold("Rowing machine photo", onNavigateUp = onNavigateUp) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PhotoPreview(current.photo, Modifier.fillMaxWidth().height(280.dp))
                Text(current.message)
                Button(onClick = viewModel::choosePhotoAgain) { Text("Try another photo") }
            }
        }
        is RowingPhotoUi.Review -> Review(current, exercises, viewModel, onCreateExercise, onNavigateUp)
    }
}

@Composable
private fun ChoosePhoto(onPhoto: (Uri) -> Unit, onNavigateUp: () -> Unit) {
    val context = LocalContext.current
    var cameraPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        cameraPhoto?.takeIf { saved }?.let(onPhoto)
    }
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(onPhoto)
    }
    AppScaffold("Rowing machine photo", onNavigateUp = onNavigateUp) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Photograph the Concept2 \"View Detail\" screen straight on, with the whole table in view.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = {
                val uri = newCameraPhotoUri(context)
                cameraPhoto = uri
                takePicture.launch(uri)
            }) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Take photo")
            }
            OutlinedButton(onClick = {
                pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                Text("Choose photo")
            }
        }
    }
}

/** A file in the cache for the camera app to write to; only the latest photo is kept. */
private fun newCameraPhotoUri(context: Context): Uri {
    val file = File(context.cacheDir, "photos/rowing-machine.jpg").apply { parentFile?.mkdirs() }
    return FileProvider.getUriForFile(context, "${context.packageName}.photos", file)
}

@Composable
private fun Review(
    review: RowingPhotoUi.Review,
    allExercises: List<ExerciseEntity>,
    viewModel: RowingPhotoViewModel,
    onCreateExercise: (TrackingType) -> Unit,
    onNavigateUp: () -> Unit,
) {
    val matching = allExercises.filter { it.trackingType == review.trackingType }
    val exerciseId = review.exerciseId?.takeIf { id -> matching.any { it.id == id } } ?: matching.singleOrNull()?.id
    val rowsValid = review.rows.all { RowingPhotoViewModel.parse(review, it.draft) != null }
    val canSave = exerciseId != null && rowsValid

    AppScaffold(
        title = "Check the results",
        onNavigateUp = onNavigateUp,
        actions = {
            TextButton(onClick = { exerciseId?.let(viewModel::save) }, enabled = canSave) { Text("Add") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                PhotoPreview(review.photo, Modifier.fillMaxWidth().height(280.dp))
                TextButton(onClick = viewModel::choosePhotoAgain) { Text("Use another photo") }
            }
            item {
                Text(Pm5Import.describe(review.workout), style = MaterialTheme.typography.titleLarge)
                Text(
                    review.trackingType.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (review.problems.isNotEmpty()) {
                item { ProblemCard(review.problems) }
            }
            item { ExerciseChoice(matching, exerciseId, review.trackingType, viewModel::selectExercise, onCreateExercise) }
            val photoDate = review.photoDate
            if (photoDate != null && photoDate != review.workoutDate) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { viewModel.setUseDate(!review.useDate) },
                    ) {
                        Checkbox(checked = review.useDate, onCheckedChange = viewModel::setUseDate)
                        Text("Set the workout's date to ${dateFormatter.format(photoDate)}")
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (review.trackingType != TrackingType.DISTANCE_SPLITS) {
                        DigitTimeField(review.rest, viewModel::setRest, "Rest", DigitFormat.MIN_SEC, Modifier.weight(1f))
                    }
                    NumberField(review.resistance, viewModel::setResistance, "Resistance", decimal = false, Modifier.weight(1f))
                }
            }
            item {
                Text(
                    if (review.trackingType == TrackingType.DISTANCE_SPLITS) "Splits" else "Intervals",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            itemsIndexed(review.rows) { index, row ->
                ImportRowFields(
                    index = index,
                    row = row,
                    type = review.trackingType,
                    valid = RowingPhotoViewModel.parse(review, row.draft) != null,
                    onChange = { viewModel.updateRow(index, it) },
                )
            }
            item {
                Button(
                    onClick = { exerciseId?.let(viewModel::save) },
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) { Text("Add ${review.rows.size} ${if (review.rows.size == 1) "set" else "sets"} to the workout") }
            }
        }
    }
}

@Composable
private fun ProblemCard(problems: List<String>) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp)) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column {
                Text("Check against the photo", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                problems.forEach { Text(it, color = MaterialTheme.colorScheme.onErrorContainer) }
            }
        }
    }
}

@Composable
private fun ExerciseChoice(
    matching: List<ExerciseEntity>,
    selectedId: Long?,
    type: TrackingType,
    onSelect: (Long) -> Unit,
    onCreate: (TrackingType) -> Unit,
) {
    Column {
        Text("Exercise", style = MaterialTheme.typography.titleMedium)
        if (matching.isEmpty()) {
            Text(
                "No exercise is tracked as ${type.label} yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        matching.forEach { exercise ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = exercise.id == selectedId, role = Role.RadioButton, onClick = { onSelect(exercise.id) }),
            ) {
                RadioButton(selected = exercise.id == selectedId, onClick = null)
                Text(exercise.name, modifier = Modifier.padding(start = 8.dp))
            }
        }
        TextButton(onClick = { onCreate(type) }) { Text("New ${type.label} exercise") }
    }
}

@Composable
private fun ImportRowFields(index: Int, row: ImportRow, type: TrackingType, valid: Boolean, onChange: (SetDraft) -> Unit) {
    val draft = row.draft
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", modifier = Modifier.width(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (type == TrackingType.INTERVALS_TIME) {
                DigitTimeField(draft.workTime, { onChange(draft.copy(workTime = it)) }, "Time", DigitFormat.MIN_SEC, Modifier.weight(1f))
            } else {
                NumberField(draft.distanceMeters, { onChange(draft.copy(distanceMeters = it)) }, "Meters", decimal = false, Modifier.weight(1f))
            }
            DigitTimeField(draft.split, { onChange(draft.copy(split = it)) }, "Split", DigitFormat.SPLIT, Modifier.weight(1f))
            NumberField(draft.strokeRate, { onChange(draft.copy(strokeRate = it)) }, "s/m", decimal = false, Modifier.weight(0.7f))
        }
        val message = row.problem ?: if (!valid) "Fill in the distance or time and the split" else null
        if (message != null) {
            Text(
                message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 28.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun PhotoPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) { loadScaled(context, uri, maxSize = 1600) }
    }
    bitmap?.let {
        Image(it, contentDescription = "Photo of the rowing machine screen", modifier = modifier, contentScale = ContentScale.Fit)
    }
}

/** Decodes a downsized copy for display; phone photos are far bigger than the screen needs. */
private fun loadScaled(context: Context, uri: Uri, maxSize: Int): ImageBitmap? = runCatching {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // Also applies the photo's EXIF rotation.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = maxSize.toFloat() / max(info.size.width, info.size.height)
            if (scale < 1) decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }.asImageBitmap()
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    }
}.getOrNull()

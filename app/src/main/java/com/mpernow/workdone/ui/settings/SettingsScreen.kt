package com.mpernow.workdone.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.backup.BackupException
import com.mpernow.workdone.data.backup.BackupManager
import com.mpernow.workdone.data.backup.DatabaseSummary
import com.mpernow.workdone.data.backup.ImportPreview
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.appViewModelFactory
import com.mpernow.workdone.ui.common.formatDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private val REST_TIMER_OPTIONS = listOf(0, 60, 90, 120, 180, 240)

sealed interface BackupState {
    data object Idle : BackupState
    data object Working : BackupState
    data object Exported : BackupState
    data class ConfirmImport(val preview: ImportPreview) : BackupState
    data class Failed(val message: String) : BackupState
}

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val backupManager: BackupManager,
) : ViewModel() {
    val weightUnit: StateFlow<WeightUnit> = settingsRepository.weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    val restTimerSeconds: StateFlow<Int> = settingsRepository.restTimerSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 90)

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settingsRepository.setWeightUnit(unit) }
    }

    fun setRestTimerSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setRestTimerSeconds(seconds) }
    }

    fun export(destination: Uri) = runBackupStep {
        backupManager.export(destination)
        _backupState.value = BackupState.Exported
    }

    fun prepareImport(source: Uri) = runBackupStep {
        _backupState.value = BackupState.ConfirmImport(backupManager.prepareImport(source))
    }

    /** On success the app restarts, so there's no state to set afterwards. */
    fun confirmImport() = runBackupStep { backupManager.completeImport() }

    fun cancelImport() {
        backupManager.discardImport()
        _backupState.value = BackupState.Idle
    }

    fun dismissBackupMessage() {
        _backupState.value = BackupState.Idle
    }

    private fun runBackupStep(step: suspend () -> Unit) {
        _backupState.value = BackupState.Working
        viewModelScope.launch {
            try {
                step()
            } catch (e: BackupException) {
                _backupState.value = BackupState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                _backupState.value = BackupState.Failed("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}

private fun restLabel(seconds: Int) = if (seconds == 0) "Off" else SetInput.formatDuration(seconds)

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(
        factory = appViewModelFactory { SettingsViewModel(it.settingsRepository, it.backupManager) },
    ),
) {
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val restTimerSeconds by viewModel.restTimerSeconds.collectAsStateWithLifecycle()
    val backupState by viewModel.backupState.collectAsStateWithLifecycle()
    var restMenuOpen by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.sqlite3"),
    ) { uri -> uri?.let(viewModel::export) }
    // Backups may come back from cloud storage without a recognised type, so allow any file.
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::prepareImport)
    }

    AppScaffold(title = "Settings") { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            ListItem(
                headlineContent = { Text("Weight unit") },
                trailingContent = {
                    SingleChoiceSegmentedButtonRow {
                        WeightUnit.entries.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = unit == weightUnit,
                                onClick = { viewModel.setWeightUnit(unit) },
                                shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                            ) { Text(unit.label) }
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Rest timer") },
                supportingContent = { Text("Starts after each logged set") },
                trailingContent = {
                    Box {
                        TextButton(onClick = { restMenuOpen = true }) { Text(restLabel(restTimerSeconds)) }
                        DropdownMenu(expanded = restMenuOpen, onDismissRequest = { restMenuOpen = false }) {
                            REST_TIMER_OPTIONS.forEach { seconds ->
                                DropdownMenuItem(
                                    text = { Text(restLabel(seconds)) },
                                    onClick = { viewModel.setRestTimerSeconds(seconds); restMenuOpen = false },
                                )
                            }
                        }
                    }
                },
            )

            Text(
                "Backup",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text("Export data") },
                supportingContent = { Text("Save all workouts and exercises to a file") },
                leadingContent = { Icon(Icons.Filled.Upload, contentDescription = null) },
                modifier = Modifier.clickable { exportLauncher.launch("workdone-backup-${LocalDate.now()}.db") },
            )
            ListItem(
                headlineContent = { Text("Import data") },
                supportingContent = { Text("Replace all data with a backup file") },
                leadingContent = { Icon(Icons.Filled.Download, contentDescription = null) },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("*/*")) },
            )
            Text(
                "Backups include exercises and workouts. Settings on this screen aren't included.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }

    BackupDialogs(backupState, viewModel)
}

@Composable
private fun BackupDialogs(state: BackupState, viewModel: SettingsViewModel) {
    when (state) {
        BackupState.Idle -> Unit
        BackupState.Working -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Working…", modifier = Modifier.padding(start = 16.dp))
                }
            },
            confirmButton = {},
        )
        BackupState.Exported -> AlertDialog(
            onDismissRequest = viewModel::dismissBackupMessage,
            title = { Text("Backup saved") },
            text = { Text("Keep it somewhere other than this phone, such as cloud storage.") },
            confirmButton = { TextButton(onClick = viewModel::dismissBackupMessage) { Text("OK") } },
        )
        is BackupState.ConfirmImport -> AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text("Replace all data?") },
            text = {
                Text(
                    "The backup has ${describe(state.preview.backup)}.\n\n" +
                        "It replaces everything in the app now: ${describe(state.preview.current)}. " +
                        "This can't be undone. The app restarts afterwards.",
                )
            },
            confirmButton = { TextButton(onClick = viewModel::confirmImport) { Text("Replace") } },
            dismissButton = { TextButton(onClick = viewModel::cancelImport) { Text("Cancel") } },
        )
        is BackupState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismissBackupMessage,
            title = { Text("Backup failed") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = viewModel::dismissBackupMessage) { Text("OK") } },
        )
    }
}

private fun describe(summary: DatabaseSummary): String = when (summary.workouts) {
    0 -> "no workouts"
    1 -> "1 workout, from ${formatDate(summary.latestWorkout!!)}"
    else -> "${summary.workouts} workouts, the latest from ${summary.latestWorkout?.let(::formatDate)}"
}

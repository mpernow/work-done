package com.mpernow.workdone.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ListItem
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
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.domain.SetInput
import com.mpernow.workdone.domain.WeightUnit
import com.mpernow.workdone.ui.common.AppScaffold
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val REST_TIMER_OPTIONS = listOf(0, 60, 90, 120, 180, 240)

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val weightUnit: StateFlow<WeightUnit> = settingsRepository.weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    val restTimerSeconds: StateFlow<Int> = settingsRepository.restTimerSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 90)

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settingsRepository.setWeightUnit(unit) }
    }

    fun setRestTimerSeconds(seconds: Int) {
        viewModelScope.launch { settingsRepository.setRestTimerSeconds(seconds) }
    }
}

private fun restLabel(seconds: Int) = if (seconds == 0) "Off" else SetInput.formatDuration(seconds)

// Phase 6: JSON/CSV export and import through the system file picker.
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = appViewModelFactory { SettingsViewModel(it.settingsRepository) }),
) {
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    val restTimerSeconds by viewModel.restTimerSeconds.collectAsStateWithLifecycle()
    var restMenuOpen by remember { mutableStateOf(false) }

    AppScaffold(title = "Settings") { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
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
            ListItem(
                headlineContent = { Text("Export / import data") },
                supportingContent = { Text("Coming soon") },
            )
        }
    }
}

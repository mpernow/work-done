package com.mpernow.workdone.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ListItem
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mpernow.workdone.data.settings.SettingsRepository
import com.mpernow.workdone.data.settings.WeightUnit
import com.mpernow.workdone.ui.common.appViewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {
    val weightUnit: StateFlow<WeightUnit> = settingsRepository.weightUnit
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    fun setWeightUnit(unit: WeightUnit) {
        viewModelScope.launch { settingsRepository.setWeightUnit(unit) }
    }
}

// Phase 6: JSON/CSV export and import through the system file picker.
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = appViewModelFactory { SettingsViewModel(it.settingsRepository) }),
) {
    val weightUnit by viewModel.weightUnit.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        ListItem(
            headlineContent = { Text("Weight unit") },
            trailingContent = {
                SingleChoiceSegmentedButtonRow {
                    WeightUnit.entries.forEachIndexed { index, unit ->
                        SegmentedButton(
                            selected = unit == weightUnit,
                            onClick = { viewModel.setWeightUnit(unit) },
                            shape = SegmentedButtonDefaults.itemShape(index, WeightUnit.entries.size),
                        ) { Text(unit.name.lowercase()) }
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

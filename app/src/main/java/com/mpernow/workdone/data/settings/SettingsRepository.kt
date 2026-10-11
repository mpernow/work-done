package com.mpernow.workdone.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class WeightUnit { KG, LB }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val weightUnit: Flow<WeightUnit> = dataStore.data.map { prefs ->
        prefs[WEIGHT_UNIT]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG
    }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[WEIGHT_UNIT] = unit.name }
    }

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
    }
}

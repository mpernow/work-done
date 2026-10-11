package com.mpernow.workdone.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mpernow.workdone.domain.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val weightUnit: Flow<WeightUnit> = dataStore.data.map { prefs ->
        prefs[WEIGHT_UNIT]?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() } ?: WeightUnit.KG
    }

    /** Rest timer length after each set; 0 turns the timer off. */
    val restTimerSeconds: Flow<Int> = dataStore.data.map { prefs -> prefs[REST_TIMER_SECONDS] ?: 90 }

    suspend fun setWeightUnit(unit: WeightUnit) {
        dataStore.edit { it[WEIGHT_UNIT] = unit.name }
    }

    suspend fun setRestTimerSeconds(seconds: Int) {
        dataStore.edit { it[REST_TIMER_SECONDS] = seconds }
    }

    private companion object {
        val WEIGHT_UNIT = stringPreferencesKey("weight_unit")
        val REST_TIMER_SECONDS = intPreferencesKey("rest_timer_seconds")
    }
}

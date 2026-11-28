package com.example.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.firstLaunchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "first_launch_datastore"
)

class FirstLaunchDataStore(context: Context) {
    private val dataStore = context.applicationContext.firstLaunchDataStore

    val firstLaunchHandled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_FIRST_LAUNCH_HANDLED] ?: false
    }

    suspend fun markFirstLaunchHandled() {
        dataStore.edit { prefs ->
            prefs[KEY_FIRST_LAUNCH_HANDLED] = true
        }
    }

    private companion object {
        val KEY_FIRST_LAUNCH_HANDLED = booleanPreferencesKey("first_launch_prompt_handled")
    }
}

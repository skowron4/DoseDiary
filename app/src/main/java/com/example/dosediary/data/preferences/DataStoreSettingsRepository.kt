package com.example.dosediary.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.example.dosediary.domain.model.UserSettings
import com.example.dosediary.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<UserSettings> = dataStore.data
        .catch { e ->
            // A corrupted/unreadable file falls back to defaults rather than crashing the app.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            UserSettings(
                darkModeEnabled = prefs[DARK_MODE],
                biometricsEnabled = prefs[BIOMETRICS_ENABLED] ?: true,
                screenProtectionEnabled = prefs[SCREEN_PROTECTION] ?: true,
            )
        }
        .distinctUntilChanged()

    override suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[DARK_MODE] = enabled }
    }

    override suspend fun setBiometricsEnabled(enabled: Boolean) {
        dataStore.edit { it[BIOMETRICS_ENABLED] = enabled }
    }

    override suspend fun setScreenProtectionEnabled(enabled: Boolean) {
        dataStore.edit { it[SCREEN_PROTECTION] = enabled }
    }

    private companion object {
        val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
        val BIOMETRICS_ENABLED = booleanPreferencesKey("biometrics_enabled")
        val SCREEN_PROTECTION = booleanPreferencesKey("screen_protection_enabled")
    }
}

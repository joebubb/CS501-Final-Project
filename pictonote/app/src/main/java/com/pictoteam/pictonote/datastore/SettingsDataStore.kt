package com.pictoteam.pictonote.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
    val BASE_FONT_SIZE = floatPreferencesKey("base_font_size_sp")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val NOTIFICATION_FREQUENCY = stringPreferencesKey("notification_frequency")
    val AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled") // New Key
}

data class AppSettings(
    val isDarkMode: Boolean,
    val baseFontSize: Float,
    val notificationsEnabled: Boolean,
    val notificationFrequency: String,
    val autoSyncEnabled: Boolean // New Property
)

class SettingsDataStoreManager(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        const val DEFAULT_BASE_FONT_SIZE_SP = 16f
        const val DEFAULT_NOTIFICATION_FREQUENCY = "Daily"
        const val DEFAULT_AUTO_SYNC_ENABLED = true // Default is ON
        const val MIN_FONT_SIZE_SP = 12f
        const val MAX_FONT_SIZE_SP = 22f
    }

    val appSettingsFlow: Flow<AppSettings> = dataStore.data
        .map { preferences ->
            AppSettings(
                isDarkMode = preferences[SettingsKeys.DARK_MODE] ?: false,
                baseFontSize = preferences[SettingsKeys.BASE_FONT_SIZE] ?: DEFAULT_BASE_FONT_SIZE_SP,
                notificationsEnabled = preferences[SettingsKeys.NOTIFICATIONS_ENABLED] ?: true,
                notificationFrequency = preferences[SettingsKeys.NOTIFICATION_FREQUENCY] ?: DEFAULT_NOTIFICATION_FREQUENCY,
                autoSyncEnabled = preferences[SettingsKeys.AUTO_SYNC_ENABLED] ?: DEFAULT_AUTO_SYNC_ENABLED // Read new setting
            )
        }

    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.DARK_MODE] = enabled
        }
    }

    suspend fun updateBaseFontSize(sizeSp: Float) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.BASE_FONT_SIZE] = sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    suspend fun updateNotificationFrequency(frequency: String) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.NOTIFICATION_FREQUENCY] = frequency
        }
    }

    suspend fun updateAutoSyncEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.AUTO_SYNC_ENABLED] = enabled
        }
    }
}
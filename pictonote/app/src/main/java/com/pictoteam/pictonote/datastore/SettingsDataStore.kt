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

// Define the DataStore instance using the preferencesDataStore delegate
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SettingsKeys {
    val DARK_MODE = booleanPreferencesKey("dark_mode_enabled")
    // Store the base font size directly as a float representing sp
    val BASE_FONT_SIZE = floatPreferencesKey("base_font_size_sp")
    val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
    val NOTIFICATION_FREQUENCY = stringPreferencesKey("notification_frequency")
}

// Data class to hold the current settings state
data class AppSettings(
    val isDarkMode: Boolean,
    val baseFontSize: Float, // Font size in sp
    val notificationsEnabled: Boolean,
    val notificationFrequency: String
)

class SettingsDataStoreManager(context: Context) {

    private val dataStore = context.dataStore

    // Default values for settings
    companion object {
        const val DEFAULT_BASE_FONT_SIZE_SP = 16f
        const val DEFAULT_NOTIFICATION_FREQUENCY = "Daily"
        const val MIN_FONT_SIZE_SP = 12f
        const val MAX_FONT_SIZE_SP = 22f
    }

    // Flow to emit current settings
    val appSettingsFlow: Flow<AppSettings> = dataStore.data
        .map { preferences ->
            AppSettings(
                isDarkMode = preferences[SettingsKeys.DARK_MODE] ?: false, // Default light mode
                baseFontSize = preferences[SettingsKeys.BASE_FONT_SIZE] ?: DEFAULT_BASE_FONT_SIZE_SP,
                notificationsEnabled = preferences[SettingsKeys.NOTIFICATIONS_ENABLED] ?: true, // Default enabled
                notificationFrequency = preferences[SettingsKeys.NOTIFICATION_FREQUENCY] ?: DEFAULT_NOTIFICATION_FREQUENCY
            )
        }

    // Function to update Dark Mode setting
    suspend fun updateDarkMode(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.DARK_MODE] = enabled
        }
    }

    // Function to update Base Font Size setting
    suspend fun updateBaseFontSize(sizeSp: Float) {
        dataStore.edit { preferences ->
            // Constrain the value to be within min/max limits
            preferences[SettingsKeys.BASE_FONT_SIZE] = sizeSp.coerceIn(MIN_FONT_SIZE_SP, MAX_FONT_SIZE_SP)
        }
    }

    // Function to update Notifications Enabled setting
    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.NOTIFICATIONS_ENABLED] = enabled
        }
    }

    // Function to update Notification Frequency setting
    suspend fun updateNotificationFrequency(frequency: String) {
        dataStore.edit { preferences ->
            preferences[SettingsKeys.NOTIFICATION_FREQUENCY] = frequency
        }
    }
}
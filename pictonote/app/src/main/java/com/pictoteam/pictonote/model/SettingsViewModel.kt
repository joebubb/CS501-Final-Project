package com.pictoteam.pictonote.model

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pictoteam.pictonote.datastore.AppSettings
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStoreManager = SettingsDataStoreManager(application)

    // Expose the settings flow as StateFlow for the UI to observe
    val appSettings: StateFlow<AppSettings> = settingsDataStoreManager.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000), // Keep active 5s after UI stops observing
            initialValue = AppSettings( // Provide initial defaults before DataStore loads
                isDarkMode = false,
                baseFontSize = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP,
                notificationsEnabled = true,
                notificationFrequency = SettingsDataStoreManager.DEFAULT_NOTIFICATION_FREQUENCY
            )
        )

    // Function called by UI to update dark mode
    fun updateDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStoreManager.updateDarkMode(enabled)
        }
    }

    // Function called by UI to update base font size
    fun updateBaseFontSize(sizeSp: Float) {
        viewModelScope.launch {
            settingsDataStoreManager.updateBaseFontSize(sizeSp)
        }
    }

    // Function called by UI to update notification enabled state
    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStoreManager.updateNotificationsEnabled(enabled)
        }
    }

    // Function called by UI to update notification frequency
    fun updateNotificationFrequency(frequency: String) {
        viewModelScope.launch {
            settingsDataStoreManager.updateNotificationFrequency(frequency)
        }
    }
}
package com.pictoteam.pictonote.model

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.pictoteam.pictonote.AuthActivity
import com.pictoteam.pictonote.constants.JOURNAL_DIR
import com.pictoteam.pictonote.constants.JOURNAL_IMAGE_DIR
import com.pictoteam.pictonote.datastore.AppSettings
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    // Data store manager for persisting user preferences
    private val dataStoreManager = SettingsDataStoreManager(application)
    private val firebaseAuth = FirebaseAuth.getInstance()

    // Exposed settings flow for UI to observe
    val appSettings: StateFlow<AppSettings> = dataStoreManager.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings(
                isDarkMode = false,
                baseFontSize = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP,
                notificationsEnabled = true,
                notificationFrequency = SettingsDataStoreManager.DEFAULT_NOTIFICATION_FREQUENCY,
                autoSyncEnabled = SettingsDataStoreManager.DEFAULT_AUTO_SYNC_ENABLED
            )
        )

    // Toggle dark mode setting
    fun updateDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateDarkMode(enabled)
        }
    }

    // Update text size throughout the app
    fun updateBaseFontSize(sizeSp: Float) {
        viewModelScope.launch {
            dataStoreManager.updateBaseFontSize(sizeSp)
        }
    }

    // Toggle notification permission
    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateNotificationsEnabled(enabled)
        }
    }

    // Change notification schedule
    fun updateNotificationFrequency(frequency: String) {
        viewModelScope.launch {
            dataStoreManager.updateNotificationFrequency(frequency)
        }
    }

    // Toggle cloud sync feature
    fun updateAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateAutoSyncEnabled(enabled)
        }
    }

    // Helper function to clean up local data during logout
    private suspend fun clearLocalUserData(application: Application) = withContext(Dispatchers.IO) {
        try {
            val journalDir = File(application.filesDir, JOURNAL_DIR)
            val imageDir = File(application.filesDir, JOURNAL_IMAGE_DIR)

            var journalDeleted = true
            var imagesDeleted = true

            if (journalDir.exists()) {
                journalDeleted = journalDir.deleteRecursively()
                if (journalDeleted) Log.i("Logout", "Local journal directory deleted.")
                else Log.e("Logout", "Failed to delete local journal directory.")
            }
            if (imageDir.exists()) {
                imagesDeleted = imageDir.deleteRecursively()
                if (imagesDeleted) Log.i("Logout", "Local image directory deleted.")
                else Log.e("Logout", "Failed to delete local image directory.")
            }
            return@withContext journalDeleted && imagesDeleted
        } catch (e: Exception) {
            Log.e("Logout", "Error clearing local user data: ${e.message}", e)
            return@withContext false
        }
    }

    // Full logout process including data cleanup
    fun logoutUser(activity: Activity) {
        viewModelScope.launch {
            try {
                Log.d("SettingsViewModel", "Attempting to log out...")

                // First clear local data
                val localDataCleared = clearLocalUserData(getApplication())
                if (!localDataCleared) {
                    // Continue with logout even if data clearing had issues
                    Log.w("SettingsViewModel", "Could not fully clear all local data.")
                }

                // Sign out from Firebase
                firebaseAuth.signOut()
                Log.d("SettingsViewModel", "Firebase sign-out successful.")

                // Sign out from Google account
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("308837841018-apsd672boajq36mle8bt760slb0knhlm.apps.googleusercontent.com")
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(activity, gso).signOut().await()
                Log.d("SettingsViewModel", "Google Sign-In client sign-out successful.")

                // Navigate back to login screen
                val intent = Intent(activity, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                activity.startActivity(intent)
                Log.d("SettingsViewModel", "Navigated to AuthActivity.")

                // Close current activity
                activity.finish()
                Log.d("SettingsViewModel", "MainActivity finished.")

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during logout: ${e.message}", e)
                Toast.makeText(activity, "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
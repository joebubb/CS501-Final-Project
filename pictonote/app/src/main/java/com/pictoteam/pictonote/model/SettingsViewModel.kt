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

    private val dataStoreManager = SettingsDataStoreManager(application) // Corrected variable name
    private val firebaseAuth = FirebaseAuth.getInstance()

    val appSettings: StateFlow<AppSettings> = dataStoreManager.appSettingsFlow // Used corrected variable name
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings( // Ensure this matches AppSettings data class
                isDarkMode = false,
                baseFontSize = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP,
                notificationsEnabled = true,
                notificationFrequency = SettingsDataStoreManager.DEFAULT_NOTIFICATION_FREQUENCY,
                autoSyncEnabled = SettingsDataStoreManager.DEFAULT_AUTO_SYNC_ENABLED // Use default from companion
            )
        )

    fun updateDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateDarkMode(enabled)
        }
    }

    fun updateBaseFontSize(sizeSp: Float) {
        viewModelScope.launch {
            dataStoreManager.updateBaseFontSize(sizeSp)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateNotificationsEnabled(enabled)
        }
    }

    fun updateNotificationFrequency(frequency: String) {
        viewModelScope.launch {
            dataStoreManager.updateNotificationFrequency(frequency)
        }
    }

    fun updateAutoSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.updateAutoSyncEnabled(enabled)
        }
    }

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

    fun logoutUser(activity: Activity) {
        viewModelScope.launch {
            try {
                Log.d("SettingsViewModel", "Attempting to log out...")

                val localDataCleared = clearLocalUserData(getApplication())
                if (!localDataCleared) {
                    // Optionally inform the user if local data clearing failed, but proceed with logout.
                    Log.w("SettingsViewModel", "Could not fully clear all local data.")
                }

                firebaseAuth.signOut()
                Log.d("SettingsViewModel", "Firebase sign-out successful.")

                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("308837841018-apsd672boajq36mle8bt760slb0knhlm.apps.googleusercontent.com")
                    .requestEmail()
                    .build()
                GoogleSignIn.getClient(activity, gso).signOut().await()
                Log.d("SettingsViewModel", "Google Sign-In client sign-out successful.")

                val intent = Intent(activity, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                activity.startActivity(intent)
                Log.d("SettingsViewModel", "Navigated to AuthActivity.")

                activity.finish()
                Log.d("SettingsViewModel", "MainActivity finished.")

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during logout: ${e.message}", e)
                Toast.makeText(activity, "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
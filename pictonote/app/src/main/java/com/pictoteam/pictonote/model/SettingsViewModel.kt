// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/model/SettingsViewModel.kt
package com.pictoteam.pictonote.model

import android.app.Activity // Added
import android.app.Application
import android.content.Intent // Added
import android.util.Log // Added
import android.widget.Toast // Added
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignIn // Added
import com.google.android.gms.auth.api.signin.GoogleSignInOptions // Added
import com.google.firebase.auth.FirebaseAuth // Added
import com.pictoteam.pictonote.AuthActivity // Added
import com.pictoteam.pictonote.datastore.AppSettings
import com.pictoteam.pictonote.datastore.SettingsDataStoreManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await // Added for suspending Google Sign Out

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsDataStoreManager = SettingsDataStoreManager(application)
    private val firebaseAuth = FirebaseAuth.getInstance() // Get Firebase Auth instance

    val appSettings: StateFlow<AppSettings> = settingsDataStoreManager.appSettingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings(
                isDarkMode = false,
                baseFontSize = SettingsDataStoreManager.DEFAULT_BASE_FONT_SIZE_SP,
                notificationsEnabled = true,
                notificationFrequency = SettingsDataStoreManager.DEFAULT_NOTIFICATION_FREQUENCY
            )
        )

    fun updateDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStoreManager.updateDarkMode(enabled)
        }
    }

    fun updateBaseFontSize(sizeSp: Float) {
        viewModelScope.launch {
            settingsDataStoreManager.updateBaseFontSize(sizeSp)
        }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStoreManager.updateNotificationsEnabled(enabled)
        }
    }

    fun updateNotificationFrequency(frequency: String) {
        viewModelScope.launch {
            settingsDataStoreManager.updateNotificationFrequency(frequency)
        }
    }

    // --- New Logout Function ---
    fun logoutUser(activity: Activity) {
        viewModelScope.launch {
            try {
                Log.d("SettingsViewModel", "Attempting to log out...")

                // 1. Sign out from Firebase
                firebaseAuth.signOut()
                Log.d("SettingsViewModel", "Firebase sign-out successful.")

                // 2. Sign out from Google
                // Use the same server client ID (requestIdToken) as in AuthActivity for consistency
                val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken("308837841018-apsd672boajq36mle8bt760slb0knhlm.apps.googleusercontent.com") // Must match AuthActivity
                    .requestEmail() // Request email to ensure client is configured similarly
                    .build()
                val googleSignInClient = GoogleSignIn.getClient(activity, gso)

                googleSignInClient.signOut().await() // Use await for suspending call
                Log.d("SettingsViewModel", "Google Sign-In client sign-out successful.")

                // 3. Navigate to AuthActivity and clear task stack
                val intent = Intent(activity, AuthActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                activity.startActivity(intent)
                Log.d("SettingsViewModel", "Navigated to AuthActivity.")

                // 4. Finish MainActivity
                activity.finish()
                Log.d("SettingsViewModel", "MainActivity finished.")

            } catch (e: Exception) {
                Log.e("SettingsViewModel", "Error during logout: ${e.message}", e)
                // Show a toast message to the user in case of error
                Toast.makeText(activity, "Logout failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
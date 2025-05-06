package com.pictoteam.pictonote

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings

class PictoNoteApplication : Application() {
    companion object {
        private const val TAG = "PictoNoteApplication"

        // Make Firestore instance accessible throughout the app
        @SuppressLint("StaticFieldLeak")
        lateinit var firestore: FirebaseFirestore
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Firebase
        Log.d(TAG, "Initializing Firebase...")
        FirebaseApp.initializeApp(this)

        // Configure Firestore and store in companion object for access throughout the app
        configureFirestore()

        Log.d(TAG, "Firebase Initialized.")
    }

    private fun configureFirestore() {
        try {
            // Get the default Firestore instance
            val firestoreInstance = FirebaseFirestore.getInstance()

            // Apply settings
            val settings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()

            firestoreInstance.firestoreSettings = settings

            // Store in companion object
            firestore = firestoreInstance

            Log.d(TAG, "Firestore configured")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring Firestore: ${e.message}", e)
        }
    }
}
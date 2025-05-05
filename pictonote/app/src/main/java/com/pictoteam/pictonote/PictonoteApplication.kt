package com.pictoteam.pictonote // Make sure this matches your base package

import android.app.Application
import com.google.firebase.FirebaseApp
import android.util.Log // Optional: for logging confirmation

class PictoNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("PictoNoteApplication", "Initializing Firebase...")
        FirebaseApp.initializeApp(this)
        Log.d("PictoNoteApplication", "Firebase Initialized.")
        // You can initialize other application-wide components here if needed
    }
}
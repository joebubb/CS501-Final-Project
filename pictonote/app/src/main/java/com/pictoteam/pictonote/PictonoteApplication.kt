package com.pictoteam.pictonote

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings

class PictoNoteApplication : Application() {

    lateinit var firestore: FirebaseFirestore
        private set

    override fun onCreate() {
        super.onCreate()
        Log.d("PictoNoteApplication", "App onCreate: Initializing Firebase...")
        FirebaseApp.initializeApp(this)
        Log.d("PictoNoteApplication", "Firebase Initialized.")

        firestore = FirebaseFirestore.getInstance().apply {
            this.firestoreSettings = firestoreSettings {
                setLocalCacheSettings(persistentCacheSettings { })
            }
        }
        Log.d("PictoNoteApplication", "Firestore Initialized for this application instance.")
    }
}

// Extension property to easily access firestore from any context that has access to applicationContext
val Context.appFirestore: FirebaseFirestore
    get() = (this.applicationContext as PictoNoteApplication).firestore
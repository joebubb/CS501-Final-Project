package com.pictoteam.pictonote.database

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class FirestoreViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()

    fun getDb(): FirebaseFirestore {
        return db
    }

    fun getCollectionData(
        collectionName: String,
        onSuccess: (QuerySnapshot) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        db.collection(collectionName)
            .get()
            .addOnSuccessListener { result ->
                onSuccess(result)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }
}

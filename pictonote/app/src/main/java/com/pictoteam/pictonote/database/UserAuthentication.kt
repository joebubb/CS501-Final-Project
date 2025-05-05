package com.pictoteam.pictonote.database

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser

fun logInUserWithGoogle(
    context: Context,
    mAuth: FirebaseAuth,
    idToken: String,
    onSuccess: () -> Unit,
    onFailure: (String) -> Unit
) {
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    mAuth.signInWithCredential(credential)
        .addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onSuccess()
            } else {
                val errorMessage = task.exception?.localizedMessage ?: "Login failed."
                onFailure(errorMessage)
            }
        }
}
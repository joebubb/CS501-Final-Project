package com.pictoteam.pictonote

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider

private const val TAG = "AuthActivity"

class AuthActivity : ComponentActivity() {

    // Firebase authentication instance
    private lateinit var mAuth: FirebaseAuth
    // Google Sign-In client for authentication
    private lateinit var googleSignInClient: GoogleSignInClient
    // Launcher for the Google Sign-In intent
    private lateinit var signInLauncher: ActivityResultLauncher<Intent>
    // Flag to prevent multiple sign-in attempts
    private var isSignInInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "AuthActivity onCreate")

        // Initialize Firebase Auth
        try {
            mAuth = FirebaseAuth.getInstance()
            Log.d(TAG, "Firebase Auth initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firebase Auth: ${e.message}", e)
            Toast.makeText(this, "Firebase Auth initialization failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Register the activity result launcher for sign-in
        signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            Log.d(TAG, "Sign in result received: ${result.resultCode}")
            isSignInInProgress = false

            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                handleSignInResult(task)
            } catch (e: Exception) {
                Log.e(TAG, "Exception in sign-in result: ${e.message}", e)
                Toast.makeText(this, "Sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        // Initialize Google Sign-In client
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken("308837841018-apsd672boajq36mle8bt760slb0knhlm.apps.googleusercontent.com")
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)
            Log.d(TAG, "Google Sign-In client initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Google Sign-In client: ${e.message}", e)
            Toast.makeText(this, "Google Sign-In initialization failed", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Set up minimal splash screen UI
        setContent {
            MinimalComposeContent()
        }

        // Slight delay before checking auth state for better UX
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            checkAuthAndProceed()
        }, 1500)
    }

    // Simple splash screen UI
    @Composable
    private fun MinimalComposeContent() {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("PictoNote ✨")
        }
    }

    // Check if user is already signed in
    private fun checkAuthAndProceed() {
        Log.d(TAG, "Checking authentication status")

        val currentUser = mAuth.currentUser
        if (currentUser != null) {
            Log.d(TAG, "User already signed in: ${currentUser.email}")
            navigateToMain()
        } else {
            // Try silent sign-in first to avoid showing the sign-in UI
            Log.d(TAG, "Attempting silent sign-in to refresh token")
            googleSignInClient.silentSignIn()
                .addOnSuccessListener { account ->
                    Log.d(TAG, "Silent sign-in successful, account email: ${account.email}")
                    firebaseAuthWithGoogle(account)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Silent sign-in failed: ${e.message}")
                    // Fall back to explicit sign-in
                    signInWithGoogle()
                }
        }
    }

    // Launch the Google Sign-In flow
    private fun signInWithGoogle() {
        if (isSignInInProgress) {
            Log.d(TAG, "Sign-in already in progress")
            return
        }

        Log.d(TAG, "Starting Google sign-in process")
        isSignInInProgress = true

        try {
            val signInIntent = googleSignInClient.signInIntent
            signInLauncher.launch(signInIntent)
        } catch (e: Exception) {
            isSignInInProgress = false
            Log.e(TAG, "Error launching sign-in: ${e.message}", e)
            Toast.makeText(this, "Failed to start sign-in", Toast.LENGTH_SHORT).show()
        }
    }

    // Process the Google Sign-In result
    private fun handleSignInResult(completedTask: Task<GoogleSignInAccount>) {
        try {
            val account = completedTask.getResult(ApiException::class.java)
            Log.d(TAG, "Google sign-in successful, account email: ${account.email}")
            firebaseAuthWithGoogle(account)
        } catch (e: ApiException) {
            Log.e(TAG, "Google sign-in failed with code: ${e.statusCode}", e)
            Toast.makeText(this, "Google sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error in sign-in: ${e.message}", e)
            Toast.makeText(this, "Sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Authenticate with Firebase using Google credentials
    private fun firebaseAuthWithGoogle(account: GoogleSignInAccount) {
        Log.d(TAG, "Authenticating with Firebase using Google token")

        val idToken = account.idToken
        if (idToken == null) {
            Log.e(TAG, "ID token is null")
            Toast.makeText(this, "Authentication failed: No ID token", Toast.LENGTH_SHORT).show()
            return
        }

        val credential = GoogleAuthProvider.getCredential(idToken, null)
        mAuth.signInWithCredential(credential)
            .addOnSuccessListener { authResult ->
                val user = authResult.user
                Log.d(TAG, "Firebase authentication successful: ${user?.email}")
                navigateToMain()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Firebase authentication failed: ${e.message}", e)
                Toast.makeText(this, "Firebase auth failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Navigate to main app screen after successful authentication
    private fun navigateToMain() {
        Log.d(TAG, "Navigating to MainActivity")
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}

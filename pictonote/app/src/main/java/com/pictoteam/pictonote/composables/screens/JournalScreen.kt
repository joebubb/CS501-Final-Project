// /Users/josephbubb/Documents/bu/Spring2025/CS501-Mobile/final/CS501-Final-Project/pictonote/app/src/main/java/com/pictoteam/pictonote/composables/screens/JournalScreen.kt
package com.pictoteam.pictonote.composables.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor // For BasicTextField text style
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle // For BasicTextField text style
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil3.compose.rememberAsyncImagePainter
import com.google.common.util.concurrent.ListenableFuture
import com.pictoteam.pictonote.model.GeminiViewModel
import com.pictoteam.pictonote.model.JournalViewModel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.File

@Composable
fun JournalScreen(
    navController: NavHostController,
    entryFilePathToEdit: String?, // This is the ENCODED path if editing, null if creating
    journalViewModel: JournalViewModel = viewModel(),
    geminiViewModel: GeminiViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Helper suspend function to await ListenableFuture results
    suspend fun <T> ListenableFuture<T>.await(): T {
        return suspendCancellableCoroutine { continuation ->
            addListener({
                try { continuation.resume(get()) }
                catch (e: Exception) { continuation.resumeWithException(e) }
            }, ContextCompat.getMainExecutor(context)) // Use main executor for listener

            continuation.invokeOnCancellation { cancel(true) }
        }
    }

    // State from JournalViewModel
    val capturedImageUri by journalViewModel.capturedImageUri.collectAsStateWithLifecycle()
    val journalText by journalViewModel.journalText.collectAsStateWithLifecycle()
    val isSaving by journalViewModel.isSaving.collectAsStateWithLifecycle()
    // isEditing is now derived state flow in ViewModel, collect it here
    val isEditing by journalViewModel.isEditing.collectAsStateWithLifecycle()

    // State from GeminiViewModel
    val promptSuggestion by geminiViewModel.journalPromptSuggestion.observeAsState("Click 'Prompt' for suggestion")
    val isLoadingPrompt by geminiViewModel.isPromptLoading.observeAsState(false)
    val reflectionResult by geminiViewModel.journalReflection.observeAsState("")
    val isLoadingReflection by geminiViewModel.isReflectionLoading.observeAsState(false)

    // CameraX Setup
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val imageCapture: ImageCapture = remember { ImageCapture.Builder().build() }
    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA // Use back camera

    // Camera Permission Handling
    var hasCamPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamPermission = granted
        if (!granted) {
            Toast.makeText(context, "Camera permission is required to add photos.", Toast.LENGTH_LONG).show()
        }
    }

    // Effect to load entry for editing OR clear state for new entry
    LaunchedEffect(entryFilePathToEdit, journalViewModel) {
        if (entryFilePathToEdit != null) {
            // We are editing an existing entry
            Log.d("JournalScreen", "Effect: Received potential edit path (encoded): $entryFilePathToEdit")
            try {
                // DECODE the path received from navigation argument
                val decodedPath = Uri.decode(entryFilePathToEdit)
                Log.d("JournalScreen", "Effect: Decoded path for loading: $decodedPath")
                // Load the entry using the decoded path
                journalViewModel.loadEntryForEditing(context, decodedPath)
            } catch (e: IllegalArgumentException) {
                // Handle bad encoding / invalid path format
                Log.e("JournalScreen", "Error decoding file path for editing: $entryFilePathToEdit", e)
                Toast.makeText(context, "Error: Invalid entry identifier.", Toast.LENGTH_SHORT).show()
                navController.popBackStack() // Go back if path is invalid
            }
            catch (e: Exception) {
                // Handle other errors during loading (e.g., file not found by ViewModel)
                Log.e("JournalScreen", "Error loading entry for editing: $entryFilePathToEdit", e)
                Toast.makeText(context, "Error loading entry for editing.", Toast.LENGTH_SHORT).show()
                navController.popBackStack() // Go back if loading fails
            }
        } else {
            // We are creating a new entry (path is null)
            // Clear state only if the VM isn't already in a new entry state and not currently editing
            if (!journalViewModel.isNewEntryState() && !journalViewModel.isEditing.value) {
                Log.d("JournalScreen", "Effect: Clearing state for new entry.")
                journalViewModel.clearJournalState()
            } else {
                Log.d("JournalScreen", "Effect: State likely already correct for new entry or ongoing edit.")
            }
        }
    }

    // Request Camera Permission if needed
    LaunchedEffect(Unit) {
        if (!hasCamPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Get CameraProvider instance asynchronously
    LaunchedEffect(lifecycleOwner) { // Depends on lifecycle owner
        try {
            cameraProvider = cameraProviderFuture.await()
            Log.d("JournalScreen", "CameraProvider obtained.")
        } catch (e: Exception) {
            Log.e("JournalScreen", "Failed to get CameraProvider.", e)
            // Show error or handle appropriately
        }
    }

    // Bind CameraX Use Cases to Lifecycle
    LaunchedEffect(cameraProvider, hasCamPermission, lifecycleOwner) {
        if (hasCamPermission && cameraProvider != null) {
            try {
                cameraProvider?.unbindAll() // Unbind previous use cases
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                // Bind preview and image capture use cases
                cameraProvider?.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                Log.d("JournalScreen", "CameraX use cases bound.")
            } catch (exc: Exception) {
                Log.e("JournalScreen", "CameraX use case binding failed", exc)
            }
        } else {
            Log.d("JournalScreen", "Skipping CameraX binding (Permission: $hasCamPermission, Provider: ${cameraProvider != null})")
        }
    }

    // Determine Screen Content based on state
    when {
        // --- State 1: Show Camera Preview (New Entry, No Image Yet, Has Permission) ---
        !isEditing && capturedImageUri == null && hasCamPermission -> {
            Box(Modifier.fillMaxSize()) {
                AndroidView({ previewView }, Modifier.fillMaxSize()) // Camera preview fills the screen
                // Take Photo Button
                Button(
                    onClick = {
                        if (cameraProvider != null && !isSaving) {
                            takePhoto(context, imageCapture, journalViewModel::onImageCaptured)
                        } else {
                            Log.w("JournalScreen", "Photo button clicked but provider null or saving.")
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .navigationBarsPadding() // Adjust for navigation bar
                        .size(72.dp),
                    shape = CircleShape,
                    enabled = cameraProvider != null && !isSaving // Enable only when camera ready and not saving
                ) {
                    Icon(Icons.Filled.AddCircle, contentDescription = "Take Photo", modifier = Modifier.size(36.dp))
                }
            }
        }

        // --- State 2: Show Entry Form (Image Captured OR Editing Existing Entry) ---
        capturedImageUri != null || isEditing -> {
            val screenPadding = if (LocalConfiguration.current.screenWidthDp >= 600) 24.dp else 16.dp
            Column(
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding() // Adjust for status bar
                    .padding(horizontal = screenPadding)
                    .verticalScroll(rememberScrollState()) // Allow scrolling for long content
                    .padding(bottom = 16.dp) // Padding at the very bottom
            ) {
                Spacer(Modifier.height(16.dp)) // Space from top edge

                // Display Image (if available)
                if (capturedImageUri != null) {
                    Image(
                        painter = rememberAsyncImagePainter(model = capturedImageUri),
                        contentDescription = if (isEditing) "Journal image (non-editable)" else "Captured journal image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f) // Maintain aspect ratio
                            .padding(bottom = 16.dp)
                            // Add a subtle border if editing to indicate it's part of the saved entry
                            .then(if (isEditing) Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)) else Modifier)
                    )
                } else if (isEditing) {
                    // Placeholder text if editing a text-only entry
                    Text(
                        "Editing text-only entry",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }

                // Journal Text Input Area
                Text(
                    "Journal Entry",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                BasicTextField(
                    value = journalText,
                    onValueChange = { journalViewModel.updateJournalText(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 150.dp) // Ensure decent height
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .padding(horizontal = 16.dp, vertical = 12.dp), // Internal padding
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    // Placeholder implementation for BasicTextField
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (journalText.isEmpty()) {
                                Text(
                                    "Add your thoughts...",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.outline // Use outline color for placeholder
                                )
                            }
                            innerTextField() // The actual text input field
                        }
                    }
                )
                Spacer(Modifier.height(24.dp))

                // AI Assistance Section
                Text(
                    "AI Assistance",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                SuggestionCard(promptSuggestion, isLoadingPrompt)
                Spacer(Modifier.height(16.dp))
                ReflectionCard(journalText, reflectionResult, isLoadingReflection)
                Spacer(Modifier.height(16.dp))
                // AI Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { geminiViewModel.suggestJournalPrompt() },
                        enabled = !isLoadingPrompt && !isLoadingReflection && !isSaving // Disable if AI busy or saving
                    ) { Text("Prompt") }
                    Button(
                        onClick = { geminiViewModel.reflectOnJournalEntry(journalText) },
                        enabled = !isLoadingReflection && !isLoadingPrompt && journalText.isNotBlank() && !isSaving // Disable if AI busy, text empty, or saving
                    ) { Text("Reflect") }
                }
                Spacer(Modifier.height(32.dp)) // More space before final action buttons

                // Cancel / Save/Update Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp), // Bottom padding before end of screen
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cancel Button
                    OutlinedButton(
                        onClick = {
                            if (isEditing) {
                                // If editing, simply go back (likely to ViewEntryScreen)
                                navController.popBackStack()
                                // ViewModel state might reset on load or save, but clear here ensures clean state if user cancels mid-edit
                                journalViewModel.clearJournalState()
                            } else {
                                // If creating a new entry, clear the state (image & text)
                                journalViewModel.clearJournalState()
                                // Optionally navigate away, e.g., back to Home or Archive,
                                // or stay on the (now empty) Journal screen. Current behavior is to stay.
                                // navController.popBackStack() // Or navigate somewhere specific
                            }
                        },
                        enabled = !isSaving // Disable while saving
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Cancel", modifier = Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("Cancel")
                    }

                    // Save / Update Button
                    Button(
                        onClick = {
                            journalViewModel.saveJournalEntry(context) { success, wasEditingMode ->
                                if (success) {
                                    if (wasEditingMode) {
                                        // If editing succeeded, pop back (likely from JournalScreen to ViewEntryScreen)
                                        navController.popBackStack()
                                    } else {
                                        // If new entry saved successfully, the state is cleared by VM.
                                        // Stay on the (now blank) Journal screen ready for the next entry,
                                        // or navigate somewhere else if preferred.
                                        // e.g., navController.navigate(ROUTE_HOME) { popUpTo(ROUTE_HOME){ inclusive = true } }
                                    }
                                }
                                // If save failed (success is false), stay on the screen.
                                // VM keeps state, user can retry or cancel. isSaving becomes false.
                            }
                        },
                        // Enable if not saving AND (editing OR has image/text for new entry)
                        enabled = !isSaving && (isEditing || capturedImageUri != null || journalText.isNotBlank())
                    ) {
                        if (isSaving) {
                            // Show progress indicator when saving
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary, // Color suitable for button background
                                strokeWidth = 2.dp
                            )
                        } else {
                            // Show icon and text otherwise
                            val buttonText = if (isEditing) "Update Entry" else "Save Entry"
                            Icon(Icons.Default.Check, contentDescription = buttonText, modifier = Modifier.size(ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(buttonText)
                        }
                    }
                }
            }
        }

        // --- State 3: Fallback (Permission Denied or Camera Initializing) ---
        else -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(16.dp), // Add padding
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val textToShow = when {
                        !hasCamPermission -> "Camera permission needed to capture photos."
                        cameraProvider == null -> "Initializing Camera..."
                        else -> "Loading..." // Generic fallback
                    }
                    Text(
                        textToShow,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    // Show permission button only if permission is denied
                    if (!hasCamPermission) {
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant Permission")
                        }
                    }
                    // Show progress indicator if camera is initializing
                    else if (cameraProvider == null) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}


// --- Helper Composables (SuggestionCard, ReflectionCard) ---

@Composable
fun SuggestionCard(promptSuggestion: String, isLoadingPrompt: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp), // Ensure minimum height
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = promptSuggestion,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f, fill = false) // Take available space, don't expand infinitely
                    .padding(end = 8.dp) // Space between text and indicator
            )
            if (isLoadingPrompt) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun ReflectionCard(entryText: String, reflectionResult: String, isLoadingReflection: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp), // Ensure minimum height
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Determine the text to display based on loading state and content
            val textToShow = when {
                isLoadingReflection -> "Generating reflection..."
                reflectionResult.isBlank() && entryText.isNotBlank() -> "Click 'Reflect' for insights."
                reflectionResult.isBlank() && entryText.isBlank() -> "Write an entry first."
                else -> reflectionResult // Show the actual reflection if available
            }
            Text(
                text = textToShow,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f, fill = false) // Take available space
                    .padding(end = 8.dp) // Space before indicator
            )
            if (isLoadingReflection) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
    }
}


// --- Helper Function (takePhoto) ---

private fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onImageSaved: (Uri) -> Unit
) {
    // Use app's cache directory for temporary storage
    val cacheDir = context.cacheDir ?: run {
        Log.e("TakePhoto", "Cache directory is null. Cannot save photo.")
        Toast.makeText(context, "Storage Error", Toast.LENGTH_SHORT).show()
        return
    }
    // Ensure cache directory exists
    if (!cacheDir.exists()) {
        cacheDir.mkdirs()
    }

    // Create a unique filename using timestamp
    val photoFile = File(cacheDir, "PICNOTE_IMG_${System.currentTimeMillis()}.jpg")

    // Configure output options
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    // Take picture using ImageCapture use case
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context), // Execute callback on main thread
        object : ImageCapture.OnImageSavedCallback {
            override fun onError(exc: ImageCaptureException) {
                Log.e("JournalScreen", "Photo capture failed: ${exc.message}", exc)
                Toast.makeText(context, "Photo Capture Error: ${exc.message}", Toast.LENGTH_LONG).show()
            }

            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // Get the URI of the saved file
                val savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                Log.d("JournalScreen", "Photo capture succeeded: $savedUri")
                // Notify the ViewModel (or caller) about the saved image URI
                onImageSaved(savedUri)
            }
        }
    )
}